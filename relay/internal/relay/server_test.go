package relay

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func TestMailboxObjectLifecycle(t *testing.T) {
	server, store := testServer(t)
	first := createMailbox(t, server, "bootstrap-secret", http.StatusCreated)
	second := createMailbox(t, server, first.PartnerEnrollmentToken, http.StatusCreated)
	createMailbox(t, server, "another-token", http.StatusInsufficientStorage)

	objectID := "object-0000000000001"
	payload := []byte("opaque encrypted bytes")
	request := authenticatedRequest(
		http.MethodPut,
		"/v1/mailboxes/"+second.ID+"/objects/"+objectID,
		second.WriteCapability,
		bytes.NewReader(payload),
	)
	response := serve(server, request)
	if response.Code != http.StatusCreated {
		t.Fatalf("upload status = %d, body = %s", response.Code, response.Body.String())
	}

	request = authenticatedRequest(
		http.MethodGet,
		"/v1/mailboxes/"+second.ID+"/objects",
		second.ReadCapability,
		nil,
	)
	response = serve(server, request)
	if response.Code != http.StatusOK {
		t.Fatalf("list status = %d", response.Code)
	}
	var listing struct {
		Objects []ObjectMetadata `json:"objects"`
	}
	if err := json.Unmarshal(response.Body.Bytes(), &listing); err != nil {
		t.Fatal(err)
	}
	if len(listing.Objects) != 1 || listing.Objects[0].ID != objectID {
		t.Fatalf("unexpected listing: %+v", listing.Objects)
	}
	if listing.Objects[0].CreatedAt != 0 || listing.Objects[0].ExpiresAt%int64(time.Hour/time.Millisecond) != 0 {
		t.Fatalf("object timestamps were not minimized: %+v", listing.Objects[0])
	}

	request = authenticatedRequest(
		http.MethodGet,
		"/v1/mailboxes/"+second.ID+"/objects/"+objectID,
		second.ReadCapability,
		nil,
	)
	response = serve(server, request)
	if response.Code != http.StatusOK || !bytes.Equal(response.Body.Bytes(), payload) {
		t.Fatalf("download mismatch: status=%d body=%q", response.Code, response.Body.Bytes())
	}

	request = authenticatedRequest(
		http.MethodPost,
		"/v1/mailboxes/"+second.ID+"/acks/"+objectID,
		second.ReadCapability,
		nil,
	)
	if response = serve(server, request); response.Code != http.StatusNoContent {
		t.Fatalf("ack status = %d", response.Code)
	}
	if _, _, err := store.OpenObject(t.Context(), second.ID, objectID); err != ErrNotFound {
		t.Fatalf("object remains after ack: %v", err)
	}
	assertWALTruncated(t, store)
}

func TestCapabilitiesRejectWrongRole(t *testing.T) {
	server, _ := testServer(t)
	first := createMailbox(t, server, "bootstrap-secret", http.StatusCreated)
	request := authenticatedRequest(
		http.MethodGet,
		"/v1/mailboxes/"+first.ID+"/objects",
		first.WriteCapability,
		nil,
	)
	if response := serve(server, request); response.Code != http.StatusUnauthorized {
		t.Fatalf("wrong capability status = %d", response.Code)
	}
}

func TestEmptyBootstrapTokenRejectsFirstMailbox(t *testing.T) {
	server, _ := testServerWithBootstrap(t, "")
	createMailbox(t, server, "", http.StatusUnauthorized)
}

func TestPartnerReplacementRevokesOldMailboxAndWriteCapability(t *testing.T) {
	server, _ := testServer(t)
	first := createMailbox(t, server, "bootstrap-secret", http.StatusCreated)
	second := createMailbox(t, server, first.PartnerEnrollmentToken, http.StatusCreated)

	request := authenticatedRequest(
		http.MethodPost,
		"/v1/mailboxes/"+first.ID+"/partner-replacement",
		first.ReadCapability,
		nil,
	)
	response := serve(server, request)
	if response.Code != http.StatusCreated {
		t.Fatalf("replacement status = %d, body = %s", response.Code, response.Body.String())
	}
	var replacement PartnerReplacementCredentials
	if err := json.Unmarshal(response.Body.Bytes(), &replacement); err != nil {
		t.Fatal(err)
	}
	if replacement.PartnerEnrollmentToken == "" || replacement.OwnWriteCapability == "" {
		t.Fatalf("missing replacement credentials: %+v", replacement)
	}

	request = authenticatedRequest(
		http.MethodGet,
		"/v1/mailboxes/"+second.ID+"/objects",
		second.ReadCapability,
		nil,
	)
	if response = serve(server, request); response.Code != http.StatusUnauthorized {
		t.Fatalf("revoked mailbox status = %d", response.Code)
	}

	objectID := "replacement-000000001"
	request = authenticatedRequest(
		http.MethodPut,
		"/v1/mailboxes/"+first.ID+"/objects/"+objectID,
		first.WriteCapability,
		bytes.NewReader([]byte("old writer")),
	)
	if response = serve(server, request); response.Code != http.StatusUnauthorized {
		t.Fatalf("old write capability status = %d", response.Code)
	}

	third := createMailbox(t, server, replacement.PartnerEnrollmentToken, http.StatusCreated)
	request = authenticatedRequest(
		http.MethodPut,
		"/v1/mailboxes/"+first.ID+"/objects/"+objectID,
		replacement.OwnWriteCapability,
		bytes.NewReader([]byte("new writer")),
	)
	if response = serve(server, request); response.Code != http.StatusCreated {
		t.Fatalf("rotated write capability status = %d", response.Code)
	}
	if third.ID == second.ID {
		t.Fatal("replacement mailbox reused the revoked identifier")
	}
	createMailbox(t, server, replacement.PartnerEnrollmentToken, http.StatusInsufficientStorage)
}

func TestRendezvousLifecycle(t *testing.T) {
	server, store := testServer(t)
	id := "rendezvous-0000000001"
	capability := "remote-pairing-capability"
	request := authenticatedRequest(http.MethodPut, "/v1/rendezvous/"+id, capability, nil)
	request.Header.Set("X-Love-Doves-Rendezvous-Create", "1")
	if response := serve(server, request); response.Code != http.StatusNoContent {
		t.Fatalf("create status = %d", response.Code)
	}
	responseBytes := []byte("encrypted pairing response")
	request = authenticatedRequest(
		http.MethodPut,
		"/v1/rendezvous/"+id,
		capability,
		bytes.NewReader(responseBytes),
	)
	if response := serve(server, request); response.Code != http.StatusNoContent {
		t.Fatalf("put status = %d", response.Code)
	}
	request = authenticatedRequest(http.MethodGet, "/v1/rendezvous/"+id, capability, nil)
	response := serve(server, request)
	if response.Code != http.StatusOK || !bytes.Equal(response.Body.Bytes(), responseBytes) {
		t.Fatalf("get status = %d body = %q", response.Code, response.Body.Bytes())
	}
	request = authenticatedRequest(http.MethodDelete, "/v1/rendezvous/"+id, capability, nil)
	if response := serve(server, request); response.Code != http.StatusNoContent {
		t.Fatalf("delete status = %d", response.Code)
	}
	assertWALTruncated(t, store)
}

func TestExpiredObjectsAreRemoved(t *testing.T) {
	server, store := testServer(t)
	clock := time.Date(2026, time.August, 10, 12, 0, 0, 0, time.UTC)
	store.now = func() time.Time { return clock }
	first := createMailbox(t, server, "bootstrap-secret", http.StatusCreated)
	objectID := "expiring-00000000001"
	request := authenticatedRequest(
		http.MethodPut,
		"/v1/mailboxes/"+first.ID+"/objects/"+objectID,
		first.WriteCapability,
		bytes.NewReader([]byte("ciphertext")),
	)
	if response := serve(server, request); response.Code != http.StatusCreated {
		t.Fatalf("upload status = %d", response.Code)
	}
	clock = clock.Add(73 * time.Hour)
	if err := store.CleanupExpired(t.Context()); err != nil {
		t.Fatal(err)
	}
	objects, err := store.ListObjects(t.Context(), first.ID)
	if err != nil {
		t.Fatal(err)
	}
	if len(objects) != 0 {
		t.Fatalf("expired objects remain: %+v", objects)
	}
	assertWALTruncated(t, store)
}

func TestAdminRequiresConfiguredEmailAndExposesOnlyAggregates(t *testing.T) {
	server, _ := testServerWithAdmin(t, "admin@example.com")

	request := httptest.NewRequest(http.MethodGet, "/admin/", nil)
	if response := serve(server, request); response.Code != http.StatusUnauthorized {
		t.Fatalf("missing admin email status = %d", response.Code)
	}
	request = httptest.NewRequest(http.MethodGet, "/admin/", nil)
	request.Header.Set("X-Love-Doves-Admin-Email", "someone-else@example.com")
	if response := serve(server, request); response.Code != http.StatusUnauthorized {
		t.Fatalf("wrong admin email status = %d", response.Code)
	}

	mailbox := createMailbox(t, server, "bootstrap-secret", http.StatusCreated)
	payload := []byte("opaque encrypted bytes")
	objectID := "admin-test-object-0001"
	request = authenticatedRequest(
		http.MethodPut,
		"/v1/mailboxes/"+mailbox.ID+"/objects/"+objectID,
		mailbox.WriteCapability,
		bytes.NewReader(payload),
	)
	if response := serve(server, request); response.Code != http.StatusCreated {
		t.Fatalf("upload status = %d", response.Code)
	}

	request = httptest.NewRequest(http.MethodGet, "/admin/", nil)
	request.Header.Set("X-Love-Doves-Admin-Email", "ADMIN@example.com")
	response := serve(server, request)
	if response.Code != http.StatusOK {
		t.Fatalf("admin status = %d, body = %s", response.Code, response.Body.String())
	}
	body := response.Body.String()
	for _, aggregate := range []string{
		"Geräte verbunden",
		"Objekte warten",
		"1 verschlüsseltes Objekt · 22 B",
		"Betriebsprotokoll",
		"Verlauf",
		`href="/oauth2/sign_out"`,
		">ADMIN@example.com</a>",
	} {
		if !strings.Contains(body, aggregate) {
			t.Fatalf("admin body is missing %q", aggregate)
		}
	}
	for _, secret := range []string{mailbox.ID, mailbox.ReadCapability, mailbox.WriteCapability, objectID, string(payload)} {
		if strings.Contains(body, secret) {
			t.Fatalf("admin body exposes relay detail %q", secret)
		}
	}
	if got := response.Header().Get("Content-Security-Policy"); !strings.Contains(got, "default-src 'none'") {
		t.Fatalf("admin CSP = %q", got)
	}

	request = httptest.NewRequest(http.MethodGet, "/admin/styles.css", nil)
	request.Header.Set("X-Love-Doves-Admin-Email", "admin@example.com")
	response = serve(server, request)
	if response.Code != http.StatusOK || response.Header().Get("Content-Type") != "text/css; charset=utf-8" {
		t.Fatalf("admin stylesheet response = %d %q", response.Code, response.Header().Get("Content-Type"))
	}
}

func TestAdminChartUsesObservedAggregateHistory(t *testing.T) {
	server := &Server{}
	now := time.Date(2026, time.August, 13, 12, 0, 0, 0, time.UTC)
	server.recordAdminSnapshot(AdminStats{MailboxCount: 1, ObjectCount: 2}, now.Add(-23*time.Hour))
	server.recordAdminSnapshot(AdminStats{MailboxCount: 2, ObjectCount: 5}, now.Add(-12*time.Hour))

	chart := server.adminChart(AdminStats{MailboxCount: 2, ObjectCount: 4}, now)
	if chart.ObjectPeak != 5 {
		t.Fatalf("object peak = %d, want 5", chart.ObjectPeak)
	}
	if chart.DeviceRange != "1–2" {
		t.Fatalf("device range = %q, want 1–2", chart.DeviceRange)
	}
	if len(chart.QueueDots) != 3 || len(chart.MailboxDots) != 3 {
		t.Fatalf("chart points = %d/%d, want 3/3", len(chart.QueueDots), len(chart.MailboxDots))
	}
	if chart.QueueDots[2].X != "718" || chart.QueueArea == "" {
		t.Fatalf("current chart point/area = %q/%q", chart.QueueDots[2].X, chart.QueueArea)
	}
}

func TestAdminIsDisabledWithoutConfiguredEmail(t *testing.T) {
	server, _ := testServer(t)
	request := httptest.NewRequest(http.MethodGet, "/admin/", nil)
	request.Header.Set("X-Love-Doves-Admin-Email", "admin@example.com")
	if response := serve(server, request); response.Code != http.StatusNotFound {
		t.Fatalf("disabled admin status = %d", response.Code)
	}
}

func TestFormatAdminBytes(t *testing.T) {
	for byteCount, expected := range map[int64]string{
		0:                  "0 B",
		22:                 "22 B",
		13 * 1024 * 1024:   "13,0 MB",
		1536 * 1024 * 1024: "1,5 GB",
	} {
		if got := formatAdminBytes(byteCount); got != expected {
			t.Errorf("formatAdminBytes(%d) = %q, want %q", byteCount, got, expected)
		}
	}
}

func assertWALTruncated(t *testing.T, store *Store) {
	t.Helper()
	info, err := os.Stat(filepath.Join(store.root, "relay.db-wal"))
	if errors.Is(err, os.ErrNotExist) {
		return
	}
	if err != nil {
		t.Fatal(err)
	}
	if info.Size() != 0 {
		t.Fatalf("relay.db-wal retains %d bytes after deletion", info.Size())
	}
}

func testServer(t *testing.T) (http.Handler, *Store) {
	return testServerWithBootstrap(t, "bootstrap-secret")
}

func testServerWithBootstrap(t *testing.T, bootstrapToken string) (http.Handler, *Store) {
	return testServerWithBootstrapAndAdmin(t, bootstrapToken, "")
}

func testServerWithAdmin(t *testing.T, adminEmail string) (http.Handler, *Store) {
	return testServerWithBootstrapAndAdmin(t, "bootstrap-secret", adminEmail)
}

func testServerWithBootstrapAndAdmin(t *testing.T, bootstrapToken, adminEmail string) (http.Handler, *Store) {
	t.Helper()
	store, err := OpenStore(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = store.Close() })
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	return NewServer(store, bootstrapToken, nil, logger, adminEmail).Handler(), store
}

func createMailbox(t *testing.T, server http.Handler, token string, expectedStatus int) MailboxCredentials {
	t.Helper()
	request := authenticatedRequest(http.MethodPost, "/v1/mailboxes", token, nil)
	response := serve(server, request)
	if response.Code != expectedStatus {
		t.Fatalf("mailbox status = %d, want %d, body = %s", response.Code, expectedStatus, response.Body.String())
	}
	if expectedStatus != http.StatusCreated {
		return MailboxCredentials{}
	}
	var credentials MailboxCredentials
	if err := json.Unmarshal(response.Body.Bytes(), &credentials); err != nil {
		t.Fatal(err)
	}
	return credentials
}

func authenticatedRequest(method, path, token string, body io.Reader) *http.Request {
	request := httptest.NewRequest(method, path, body)
	request.Header.Set("Authorization", "Bearer "+token)
	return request
}

func serve(handler http.Handler, request *http.Request) *httptest.ResponseRecorder {
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, request)
	return recorder
}
