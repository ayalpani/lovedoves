package relay

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sync"
	"time"

	_ "modernc.org/sqlite"
)

var (
	ErrUnauthorized      = errors.New("unauthorized")
	ErrNotFound          = errors.New("not found")
	ErrConflict          = errors.New("conflict")
	ErrLimit             = errors.New("limit exceeded")
	ErrBootstrapConsumed = errors.New("bootstrap consumed")
)

type MailboxCredentials struct {
	ID                     string `json:"mailbox_id"`
	ReadCapability         string `json:"read_capability"`
	WriteCapability        string `json:"write_capability"`
	PartnerEnrollmentToken string `json:"partner_enrollment_token,omitempty"`
}

type PartnerReplacementCredentials struct {
	PartnerEnrollmentToken string `json:"partner_enrollment_token"`
	OwnWriteCapability     string `json:"own_write_capability"`
}

type ObjectMetadata struct {
	ID           string `json:"object_id"`
	Size         int64  `json:"size"`
	CreatedAt    int64  `json:"created_at_epoch_ms"`
	ExpiresAt    int64  `json:"expires_at_epoch_ms"`
	CipherSHA256 string `json:"cipher_sha256"`
}

type Store struct {
	db   *sql.DB
	root string
	now  func() time.Time
	mu   sync.Mutex
}

func OpenStore(root string) (*Store, error) {
	if err := os.MkdirAll(root, 0o700); err != nil {
		return nil, fmt.Errorf("create data directory: %w", err)
	}
	for _, child := range []string{"objects", "rendezvous"} {
		if err := os.MkdirAll(filepath.Join(root, child), 0o700); err != nil {
			return nil, fmt.Errorf("create %s directory: %w", child, err)
		}
	}
	db, err := sql.Open("sqlite", filepath.Join(root, "relay.db"))
	if err != nil {
		return nil, fmt.Errorf("open relay database: %w", err)
	}
	db.SetMaxOpenConns(1)
	store := &Store{db: db, root: root, now: time.Now}
	if err := store.initialize(context.Background()); err != nil {
		db.Close()
		return nil, err
	}
	if err := store.truncateWAL(context.Background()); err != nil {
		db.Close()
		return nil, err
	}
	_ = os.Chmod(filepath.Join(root, "relay.db"), 0o600)
	return store, nil
}

func (s *Store) Close() error { return s.db.Close() }

func (s *Store) initialize(ctx context.Context) error {
	const schema = `
PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;
PRAGMA secure_delete=ON;
CREATE TABLE IF NOT EXISTS metadata (
  key TEXT PRIMARY KEY,
  value BLOB NOT NULL
);
CREATE TABLE IF NOT EXISTS mailboxes (
  id TEXT PRIMARY KEY,
  read_hash BLOB NOT NULL,
  write_hash BLOB NOT NULL,
  push_token TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS objects (
  mailbox_id TEXT NOT NULL,
  object_id TEXT NOT NULL,
  size INTEGER NOT NULL,
  cipher_sha256 BLOB NOT NULL,
  relative_path TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  PRIMARY KEY (mailbox_id, object_id),
  FOREIGN KEY (mailbox_id) REFERENCES mailboxes(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS objects_expiry ON objects(expires_at);
CREATE TABLE IF NOT EXISTS rendezvous (
  id TEXT PRIMARY KEY,
  capability_hash BLOB NOT NULL,
  relative_path TEXT,
  size INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  ready INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS rendezvous_expiry ON rendezvous(expires_at);
UPDATE mailboxes SET created_at = 0 WHERE created_at <> 0;
UPDATE objects
SET created_at = 0,
    expires_at = (expires_at / 3600000) * 3600000
WHERE created_at <> 0 OR expires_at % 3600000 <> 0;
`
	if _, err := s.db.ExecContext(ctx, schema); err != nil {
		return fmt.Errorf("initialize relay database: %w", err)
	}
	return nil
}

func (s *Store) CreateMailbox(ctx context.Context, token, bootstrapToken string) (MailboxCredentials, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	var count int
	if err := s.db.QueryRowContext(ctx, "SELECT COUNT(*) FROM mailboxes").Scan(&count); err != nil {
		return MailboxCredentials{}, err
	}
	if count >= 2 {
		return MailboxCredentials{}, ErrLimit
	}
	if count == 0 {
		var consumed []byte
		err := s.db.QueryRowContext(ctx, "SELECT value FROM metadata WHERE key = 'bootstrap_consumed'").Scan(&consumed)
		if err == nil {
			return MailboxCredentials{}, ErrBootstrapConsumed
		}
		if !errors.Is(err, sql.ErrNoRows) {
			return MailboxCredentials{}, err
		}
		if bootstrapToken == "" || !sameCapability(token, bootstrapToken) {
			return MailboxCredentials{}, ErrUnauthorized
		}
	} else {
		var expectedHash []byte
		if err := s.db.QueryRowContext(
			ctx,
			"SELECT value FROM metadata WHERE key = 'partner_enrollment_hash'",
		).Scan(&expectedHash); err != nil {
			return MailboxCredentials{}, ErrUnauthorized
		}
		if !matchesHash(token, expectedHash) {
			return MailboxCredentials{}, ErrUnauthorized
		}
	}

	credentials, err := newMailboxCredentials()
	if err != nil {
		return MailboxCredentials{}, err
	}
	var enrollment string
	if count == 0 {
		enrollment, err = randomToken(32)
		if err != nil {
			return MailboxCredentials{}, err
		}
		credentials.PartnerEnrollmentToken = enrollment
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return MailboxCredentials{}, err
	}
	defer tx.Rollback()
	if _, err := tx.ExecContext(
		ctx,
		"INSERT INTO mailboxes(id, read_hash, write_hash, created_at) VALUES(?, ?, ?, ?)",
		credentials.ID,
		hashCapability(credentials.ReadCapability),
		hashCapability(credentials.WriteCapability),
		0,
	); err != nil {
		return MailboxCredentials{}, err
	}
	if count == 0 {
		if _, err := tx.ExecContext(
			ctx,
			"INSERT OR REPLACE INTO metadata(key, value) VALUES('partner_enrollment_hash', ?), ('bootstrap_consumed', ?)",
			hashCapability(enrollment),
			[]byte{1},
		); err != nil {
			return MailboxCredentials{}, err
		}
	} else if _, err := tx.ExecContext(ctx, "DELETE FROM metadata WHERE key = 'partner_enrollment_hash'"); err != nil {
		return MailboxCredentials{}, err
	}
	if err := tx.Commit(); err != nil {
		return MailboxCredentials{}, err
	}
	if count != 0 {
		if err := s.truncateWAL(ctx); err != nil {
			return MailboxCredentials{}, err
		}
	}
	return credentials, nil
}

func (s *Store) AuthorizeMailbox(ctx context.Context, mailboxID, token string, write bool) error {
	column := "read_hash"
	if write {
		column = "write_hash"
	}
	var expected []byte
	err := s.db.QueryRowContext(
		ctx,
		"SELECT "+column+" FROM mailboxes WHERE id = ?",
		mailboxID,
	).Scan(&expected)
	if errors.Is(err, sql.ErrNoRows) || (err == nil && !matchesHash(token, expected)) {
		return ErrUnauthorized
	}
	return err
}

// PreparePartnerReplacement removes the other mailbox, rotates the surviving
// mailbox's write capability, and issues a one-use enrollment capability. The
// caller has already authenticated with the surviving mailbox's read
// capability. This is the only server-side state transition needed for
// partner-assisted recovery; the relay still learns no device identity.
func (s *Store) PreparePartnerReplacement(
	ctx context.Context,
	survivingMailboxID string,
) (PartnerReplacementCredentials, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	enrollment, err := randomToken(32)
	if err != nil {
		return PartnerReplacementCredentials{}, err
	}
	writeCapability, err := randomToken(32)
	if err != nil {
		return PartnerReplacementCredentials{}, err
	}
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT objects.relative_path, objects.mailbox_id
		 FROM objects JOIN mailboxes ON mailboxes.id = objects.mailbox_id
		 WHERE mailboxes.id <> ?`,
		survivingMailboxID,
	)
	if err != nil {
		return PartnerReplacementCredentials{}, err
	}
	var paths, removedMailboxes []string
	for rows.Next() {
		var path, mailboxID string
		if err := rows.Scan(&path, &mailboxID); err != nil {
			rows.Close()
			return PartnerReplacementCredentials{}, err
		}
		paths = append(paths, path)
		removedMailboxes = append(removedMailboxes, mailboxID)
	}
	if err := rows.Close(); err != nil {
		return PartnerReplacementCredentials{}, err
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return PartnerReplacementCredentials{}, err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(
		ctx,
		"UPDATE mailboxes SET write_hash = ? WHERE id = ?",
		hashCapability(writeCapability),
		survivingMailboxID,
	)
	if err != nil {
		return PartnerReplacementCredentials{}, err
	}
	changed, _ := result.RowsAffected()
	if changed != 1 {
		return PartnerReplacementCredentials{}, ErrNotFound
	}
	if _, err := tx.ExecContext(ctx, "DELETE FROM mailboxes WHERE id <> ?", survivingMailboxID); err != nil {
		return PartnerReplacementCredentials{}, err
	}
	if _, err := tx.ExecContext(
		ctx,
		"INSERT OR REPLACE INTO metadata(key, value) VALUES('partner_enrollment_hash', ?)",
		hashCapability(enrollment),
	); err != nil {
		return PartnerReplacementCredentials{}, err
	}
	if err := tx.Commit(); err != nil {
		return PartnerReplacementCredentials{}, err
	}
	for _, path := range paths {
		_ = os.Remove(filepath.Join(s.root, path))
	}
	seen := make(map[string]struct{})
	for _, mailboxID := range removedMailboxes {
		if _, exists := seen[mailboxID]; exists {
			continue
		}
		seen[mailboxID] = struct{}{}
		_ = os.Remove(filepath.Join(s.root, "objects", mailboxID))
	}
	if err := s.truncateWAL(ctx); err != nil {
		return PartnerReplacementCredentials{}, err
	}
	return PartnerReplacementCredentials{
		PartnerEnrollmentToken: enrollment,
		OwnWriteCapability:     writeCapability,
	}, nil
}

func (s *Store) PutObject(ctx context.Context, mailboxID, objectID string, body io.Reader, maxObjectBytes, maxMailboxBytes int64) (bool, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	directory := filepath.Join(s.root, "objects", mailboxID)
	if err := os.MkdirAll(directory, 0o700); err != nil {
		return false, err
	}
	temporary, err := os.CreateTemp(directory, ".upload-*")
	if err != nil {
		return false, err
	}
	temporaryName := temporary.Name()
	defer os.Remove(temporaryName)
	hasher := sha256.New()
	written, copyErr := io.Copy(io.MultiWriter(temporary, hasher), io.LimitReader(body, maxObjectBytes+1))
	if syncErr := temporary.Sync(); copyErr == nil {
		copyErr = syncErr
	}
	if closeErr := temporary.Close(); copyErr == nil {
		copyErr = closeErr
	}
	if copyErr != nil {
		return false, copyErr
	}
	if written > maxObjectBytes {
		return false, ErrLimit
	}
	digest := hasher.Sum(nil)
	var existingSize int64
	var existingHash []byte
	err = s.db.QueryRowContext(
		ctx,
		"SELECT size, cipher_sha256 FROM objects WHERE mailbox_id = ? AND object_id = ?",
		mailboxID,
		objectID,
	).Scan(&existingSize, &existingHash)
	if err == nil {
		if existingSize == written && subtle.ConstantTimeCompare(existingHash, digest) == 1 {
			return false, nil
		}
		return false, ErrConflict
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return false, err
	}
	var used int64
	if err := s.db.QueryRowContext(
		ctx,
		"SELECT COALESCE(SUM(size), 0) FROM objects WHERE mailbox_id = ?",
		mailboxID,
	).Scan(&used); err != nil {
		return false, err
	}
	if written > maxMailboxBytes-used {
		return false, ErrLimit
	}
	finalName := filepath.Join(directory, objectID)
	if err := os.Rename(temporaryName, finalName); err != nil {
		return false, err
	}
	now := s.now()
	relativePath, _ := filepath.Rel(s.root, finalName)
	_, err = s.db.ExecContext(
		ctx,
		`INSERT INTO objects(mailbox_id, object_id, size, cipher_sha256, relative_path, created_at, expires_at)
		 VALUES(?, ?, ?, ?, ?, ?, ?)`,
		mailboxID,
		objectID,
		written,
		digest,
		relativePath,
		0,
		now.Add(72*time.Hour).Truncate(time.Hour).UnixMilli(),
	)
	if err != nil {
		os.Remove(finalName)
		return false, err
	}
	return true, nil
}

func (s *Store) ListObjects(ctx context.Context, mailboxID string) ([]ObjectMetadata, error) {
	rows, err := s.db.QueryContext(
		ctx,
		`SELECT object_id, size, created_at, expires_at, cipher_sha256
		 FROM objects WHERE mailbox_id = ? AND expires_at > ? ORDER BY rowid`,
		mailboxID,
		s.now().UnixMilli(),
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := make([]ObjectMetadata, 0)
	for rows.Next() {
		var item ObjectMetadata
		var hash []byte
		if err := rows.Scan(&item.ID, &item.Size, &item.CreatedAt, &item.ExpiresAt, &hash); err != nil {
			return nil, err
		}
		item.CipherSHA256 = hex.EncodeToString(hash)
		result = append(result, item)
	}
	return result, rows.Err()
}

func (s *Store) OpenObject(ctx context.Context, mailboxID, objectID string) (*os.File, int64, error) {
	var relativePath string
	var size int64
	err := s.db.QueryRowContext(
		ctx,
		"SELECT relative_path, size FROM objects WHERE mailbox_id = ? AND object_id = ? AND expires_at > ?",
		mailboxID,
		objectID,
		s.now().UnixMilli(),
	).Scan(&relativePath, &size)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, 0, ErrNotFound
	}
	if err != nil {
		return nil, 0, err
	}
	file, err := os.Open(filepath.Join(s.root, relativePath))
	return file, size, err
}

func (s *Store) AckObject(ctx context.Context, mailboxID, objectID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	var relativePath string
	err := s.db.QueryRowContext(
		ctx,
		"SELECT relative_path FROM objects WHERE mailbox_id = ? AND object_id = ?",
		mailboxID,
		objectID,
	).Scan(&relativePath)
	if errors.Is(err, sql.ErrNoRows) {
		return ErrNotFound
	}
	if err != nil {
		return err
	}
	if _, err := s.db.ExecContext(
		ctx,
		"DELETE FROM objects WHERE mailbox_id = ? AND object_id = ?",
		mailboxID,
		objectID,
	); err != nil {
		return err
	}
	if err := os.Remove(filepath.Join(s.root, relativePath)); err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	return s.truncateWAL(ctx)
}

func (s *Store) SetPushToken(ctx context.Context, mailboxID, token string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	result, err := s.db.ExecContext(ctx, "UPDATE mailboxes SET push_token = ? WHERE id = ?", token, mailboxID)
	if err != nil {
		return err
	}
	changed, _ := result.RowsAffected()
	if changed != 1 {
		return ErrNotFound
	}
	return s.truncateWAL(ctx)
}

func (s *Store) PushToken(ctx context.Context, mailboxID string) (string, error) {
	var token string
	err := s.db.QueryRowContext(ctx, "SELECT push_token FROM mailboxes WHERE id = ?", mailboxID).Scan(&token)
	return token, err
}

func (s *Store) DeleteMailbox(ctx context.Context, mailboxID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	rows, err := s.db.QueryContext(ctx, "SELECT relative_path FROM objects WHERE mailbox_id = ?", mailboxID)
	if err != nil {
		return err
	}
	var paths []string
	for rows.Next() {
		var path string
		if err := rows.Scan(&path); err != nil {
			rows.Close()
			return err
		}
		paths = append(paths, path)
	}
	rows.Close()
	result, err := s.db.ExecContext(ctx, "DELETE FROM mailboxes WHERE id = ?", mailboxID)
	if err != nil {
		return err
	}
	changed, _ := result.RowsAffected()
	if changed != 1 {
		return ErrNotFound
	}
	for _, path := range paths {
		_ = os.Remove(filepath.Join(s.root, path))
	}
	_ = os.Remove(filepath.Join(s.root, "objects", mailboxID))
	return s.truncateWAL(ctx)
}

func (s *Store) CreateRendezvous(ctx context.Context, id, capability string) error {
	now := s.now()
	_, err := s.db.ExecContext(
		ctx,
		`INSERT INTO rendezvous(id, capability_hash, created_at, expires_at)
		 VALUES(?, ?, ?, ?)`,
		id,
		hashCapability(capability),
		now.UnixMilli(),
		now.Add(10*time.Minute).UnixMilli(),
	)
	if isUniqueViolation(err) {
		return ErrConflict
	}
	return err
}

func (s *Store) PutRendezvous(ctx context.Context, id, capability string, body io.Reader, maxBytes int64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	var expected []byte
	var ready int
	err := s.db.QueryRowContext(
		ctx,
		"SELECT capability_hash, ready FROM rendezvous WHERE id = ? AND expires_at > ?",
		id,
		s.now().UnixMilli(),
	).Scan(&expected, &ready)
	if errors.Is(err, sql.ErrNoRows) {
		return ErrNotFound
	}
	if err != nil {
		return err
	}
	if !matchesHash(capability, expected) {
		return ErrUnauthorized
	}
	if ready != 0 {
		return ErrConflict
	}
	temporary, err := os.CreateTemp(filepath.Join(s.root, "rendezvous"), ".response-*")
	if err != nil {
		return err
	}
	temporaryName := temporary.Name()
	defer os.Remove(temporaryName)
	written, err := io.Copy(temporary, io.LimitReader(body, maxBytes+1))
	if syncErr := temporary.Sync(); err == nil {
		err = syncErr
	}
	if closeErr := temporary.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		return err
	}
	if written > maxBytes {
		return ErrLimit
	}
	finalName := filepath.Join(s.root, "rendezvous", id)
	if err := os.Rename(temporaryName, finalName); err != nil {
		return err
	}
	relative, _ := filepath.Rel(s.root, finalName)
	if _, err := s.db.ExecContext(
		ctx,
		"UPDATE rendezvous SET relative_path = ?, size = ?, ready = 1 WHERE id = ?",
		relative,
		written,
		id,
	); err != nil {
		os.Remove(finalName)
		return err
	}
	return nil
}

func (s *Store) OpenRendezvous(ctx context.Context, id, capability string) (*os.File, int64, error) {
	var expected []byte
	var relative string
	var size int64
	err := s.db.QueryRowContext(
		ctx,
		"SELECT capability_hash, relative_path, size FROM rendezvous WHERE id = ? AND ready = 1 AND expires_at > ?",
		id,
		s.now().UnixMilli(),
	).Scan(&expected, &relative, &size)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, 0, ErrNotFound
	}
	if err != nil {
		return nil, 0, err
	}
	if !matchesHash(capability, expected) {
		return nil, 0, ErrUnauthorized
	}
	file, err := os.Open(filepath.Join(s.root, relative))
	return file, size, err
}

func (s *Store) DeleteRendezvous(ctx context.Context, id, capability string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	var expected []byte
	var relative sql.NullString
	err := s.db.QueryRowContext(ctx, "SELECT capability_hash, relative_path FROM rendezvous WHERE id = ?", id).Scan(&expected, &relative)
	if errors.Is(err, sql.ErrNoRows) {
		return ErrNotFound
	}
	if err != nil {
		return err
	}
	if !matchesHash(capability, expected) {
		return ErrUnauthorized
	}
	if _, err := s.db.ExecContext(ctx, "DELETE FROM rendezvous WHERE id = ?", id); err != nil {
		return err
	}
	if relative.Valid {
		_ = os.Remove(filepath.Join(s.root, relative.String))
	}
	return s.truncateWAL(ctx)
}

func (s *Store) CleanupExpired(ctx context.Context) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := s.now().UnixMilli()
	for table, query := range map[string]string{
		"objects":    "SELECT relative_path FROM objects WHERE expires_at <= ?",
		"rendezvous": "SELECT relative_path FROM rendezvous WHERE expires_at <= ? AND relative_path IS NOT NULL",
	} {
		rows, err := s.db.QueryContext(ctx, query, now)
		if err != nil {
			return err
		}
		var paths []string
		for rows.Next() {
			var path string
			if err := rows.Scan(&path); err != nil {
				rows.Close()
				return err
			}
			paths = append(paths, path)
		}
		rows.Close()
		if _, err := s.db.ExecContext(ctx, "DELETE FROM "+table+" WHERE expires_at <= ?", now); err != nil {
			return err
		}
		for _, path := range paths {
			_ = os.Remove(filepath.Join(s.root, path))
		}
	}
	return s.truncateWAL(ctx)
}

func (s *Store) truncateWAL(ctx context.Context) error {
	var busy, logPages, checkpointedPages int
	if err := s.db.QueryRowContext(ctx, "PRAGMA wal_checkpoint(TRUNCATE)").Scan(
		&busy,
		&logPages,
		&checkpointedPages,
	); err != nil {
		return fmt.Errorf("truncate relay database WAL: %w", err)
	}
	if busy != 0 || logPages != 0 || checkpointedPages != 0 {
		return fmt.Errorf("truncate relay database WAL: checkpoint incomplete")
	}
	return nil
}

func newMailboxCredentials() (MailboxCredentials, error) {
	id, err := randomToken(18)
	if err != nil {
		return MailboxCredentials{}, err
	}
	read, err := randomToken(32)
	if err != nil {
		return MailboxCredentials{}, err
	}
	write, err := randomToken(32)
	if err != nil {
		return MailboxCredentials{}, err
	}
	return MailboxCredentials{ID: id, ReadCapability: read, WriteCapability: write}, nil
}

func RandomToken(bytes int) (string, error) { return randomToken(bytes) }

func randomToken(bytes int) (string, error) {
	value := make([]byte, bytes)
	if _, err := rand.Read(value); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(value), nil
}

func hashCapability(token string) []byte {
	hash := sha256.Sum256([]byte(token))
	return hash[:]
}

func matchesHash(token string, expected []byte) bool {
	actual := hashCapability(token)
	return len(expected) == len(actual) && subtle.ConstantTimeCompare(actual, expected) == 1
}

func sameCapability(left, right string) bool {
	return subtle.ConstantTimeCompare(hashCapability(left), hashCapability(right)) == 1
}

func isUniqueViolation(err error) bool {
	return err != nil && (errors.Is(err, sql.ErrNoRows) || contains(err.Error(), "UNIQUE constraint failed"))
}

func contains(value, part string) bool {
	for i := 0; i+len(part) <= len(value); i++ {
		if value[i:i+len(part)] == part {
			return true
		}
	}
	return false
}
