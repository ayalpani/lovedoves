package relay

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"regexp"
	"strings"
	"sync"
	"time"
)

const (
	MaxObjectBytes     = int64(20 * 1024 * 1024)
	MaxMailboxBytes    = int64(1024 * 1024 * 1024)
	MaxRendezvousBytes = int64(256 * 1024)
)

var opaqueID = regexp.MustCompile(`^[A-Za-z0-9_-]{16,128}$`)

type Notifier interface {
	Notify(context.Context, string) error
}

type Server struct {
	store          *Store
	bootstrapToken string
	notifier       Notifier
	logger         *slog.Logger
	limiter        *fixedWindowLimiter
	handler        http.Handler
}

func NewServer(store *Store, bootstrapToken string, notifier Notifier, logger *slog.Logger) *Server {
	server := &Server{
		store:          store,
		bootstrapToken: bootstrapToken,
		notifier:       notifier,
		logger:         logger,
		limiter:        newFixedWindowLimiter(600, time.Minute),
	}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", server.health)
	mux.HandleFunc("POST /v1/mailboxes", server.createMailbox)
	mux.HandleFunc("POST /v1/mailboxes/{mailbox}/partner-replacement", server.preparePartnerReplacement)
	mux.HandleFunc("PUT /v1/rendezvous/{id}", server.putRendezvous)
	mux.HandleFunc("GET /v1/rendezvous/{id}", server.getRendezvous)
	mux.HandleFunc("DELETE /v1/rendezvous/{id}", server.deleteRendezvous)
	mux.HandleFunc("PUT /v1/mailboxes/{mailbox}/objects/{object}", server.putObject)
	mux.HandleFunc("GET /v1/mailboxes/{mailbox}/objects", server.listObjects)
	mux.HandleFunc("GET /v1/mailboxes/{mailbox}/objects/{object}", server.getObject)
	mux.HandleFunc("POST /v1/mailboxes/{mailbox}/acks/{object}", server.ackObject)
	mux.HandleFunc("PUT /v1/mailboxes/{mailbox}/push-token", server.putPushToken)
	mux.HandleFunc("DELETE /v1/mailboxes/{mailbox}", server.deleteMailbox)
	server.handler = server.middleware(mux)
	return server
}

func (s *Server) Handler() http.Handler { return s.handler }

func (s *Server) health(w http.ResponseWriter, _ *http.Request) { w.WriteHeader(http.StatusNoContent) }

func (s *Server) createMailbox(w http.ResponseWriter, request *http.Request) {
	credentials, err := s.store.CreateMailbox(
		request.Context(),
		bearerToken(request),
		s.bootstrapToken,
	)
	if err != nil {
		s.respondError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, credentials)
}

func (s *Server) preparePartnerReplacement(w http.ResponseWriter, request *http.Request) {
	mailbox := request.PathValue("mailbox")
	if !opaqueID.MatchString(mailbox) {
		s.respondError(w, ErrNotFound)
		return
	}
	if err := s.store.AuthorizeMailbox(request.Context(), mailbox, bearerToken(request), false); err != nil {
		s.respondError(w, err)
		return
	}
	credentials, err := s.store.PreparePartnerReplacement(request.Context(), mailbox)
	if err != nil {
		s.respondError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, credentials)
}

func (s *Server) putRendezvous(w http.ResponseWriter, request *http.Request) {
	id := request.PathValue("id")
	if !opaqueID.MatchString(id) {
		s.respondError(w, ErrNotFound)
		return
	}
	token := bearerToken(request)
	var err error
	if request.Header.Get("X-Love-Doves-Rendezvous-Create") == "1" {
		err = s.store.CreateRendezvous(request.Context(), id, token)
	} else {
		err = s.store.PutRendezvous(
			request.Context(),
			id,
			token,
			request.Body,
			MaxRendezvousBytes,
		)
	}
	if err != nil {
		s.respondError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) getRendezvous(w http.ResponseWriter, request *http.Request) {
	id := request.PathValue("id")
	if !opaqueID.MatchString(id) {
		s.respondError(w, ErrNotFound)
		return
	}
	file, size, err := s.store.OpenRendezvous(request.Context(), id, bearerToken(request))
	if err != nil {
		s.respondError(w, err)
		return
	}
	defer file.Close()
	writeOpaqueFile(w, file, size)
}

func (s *Server) deleteRendezvous(w http.ResponseWriter, request *http.Request) {
	id := request.PathValue("id")
	if !opaqueID.MatchString(id) {
		s.respondError(w, ErrNotFound)
		return
	}
	if err := s.store.DeleteRendezvous(request.Context(), id, bearerToken(request)); err != nil {
		s.respondError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) putObject(w http.ResponseWriter, request *http.Request) {
	mailbox, object := request.PathValue("mailbox"), request.PathValue("object")
	if !validObjectPath(mailbox, object) {
		s.respondError(w, ErrNotFound)
		return
	}
	if err := s.store.AuthorizeMailbox(request.Context(), mailbox, bearerToken(request), true); err != nil {
		s.respondError(w, err)
		return
	}
	created, err := s.store.PutObject(
		request.Context(),
		mailbox,
		object,
		request.Body,
		MaxObjectBytes,
		MaxMailboxBytes,
	)
	if err != nil {
		s.respondError(w, err)
		return
	}
	if created {
		s.wake(mailbox)
		w.WriteHeader(http.StatusCreated)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) listObjects(w http.ResponseWriter, request *http.Request) {
	mailbox := request.PathValue("mailbox")
	if !opaqueID.MatchString(mailbox) {
		s.respondError(w, ErrNotFound)
		return
	}
	if err := s.store.AuthorizeMailbox(request.Context(), mailbox, bearerToken(request), false); err != nil {
		s.respondError(w, err)
		return
	}
	objects, err := s.store.ListObjects(request.Context(), mailbox)
	if err != nil {
		s.respondError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"objects": objects})
}

func (s *Server) getObject(w http.ResponseWriter, request *http.Request) {
	mailbox, object := request.PathValue("mailbox"), request.PathValue("object")
	if !validObjectPath(mailbox, object) {
		s.respondError(w, ErrNotFound)
		return
	}
	if err := s.store.AuthorizeMailbox(request.Context(), mailbox, bearerToken(request), false); err != nil {
		s.respondError(w, err)
		return
	}
	file, size, err := s.store.OpenObject(request.Context(), mailbox, object)
	if err != nil {
		s.respondError(w, err)
		return
	}
	defer file.Close()
	writeOpaqueFile(w, file, size)
}

func (s *Server) ackObject(w http.ResponseWriter, request *http.Request) {
	mailbox, object := request.PathValue("mailbox"), request.PathValue("object")
	if !validObjectPath(mailbox, object) {
		s.respondError(w, ErrNotFound)
		return
	}
	if err := s.store.AuthorizeMailbox(request.Context(), mailbox, bearerToken(request), false); err != nil {
		s.respondError(w, err)
		return
	}
	if err := s.store.AckObject(request.Context(), mailbox, object); err != nil {
		s.respondError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) putPushToken(w http.ResponseWriter, request *http.Request) {
	mailbox := request.PathValue("mailbox")
	if !opaqueID.MatchString(mailbox) {
		s.respondError(w, ErrNotFound)
		return
	}
	if err := s.store.AuthorizeMailbox(request.Context(), mailbox, bearerToken(request), false); err != nil {
		s.respondError(w, err)
		return
	}
	var payload struct {
		Token string `json:"token"`
	}
	decoder := json.NewDecoder(io.LimitReader(request.Body, 4097))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&payload); err != nil || len(payload.Token) > 4096 {
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "invalid_request"})
		return
	}
	if err := s.store.SetPushToken(request.Context(), mailbox, payload.Token); err != nil {
		s.respondError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) deleteMailbox(w http.ResponseWriter, request *http.Request) {
	mailbox := request.PathValue("mailbox")
	if !opaqueID.MatchString(mailbox) {
		s.respondError(w, ErrNotFound)
		return
	}
	if err := s.store.AuthorizeMailbox(request.Context(), mailbox, bearerToken(request), false); err != nil {
		s.respondError(w, err)
		return
	}
	if err := s.store.DeleteMailbox(request.Context(), mailbox); err != nil {
		s.respondError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) wake(mailbox string) {
	if s.notifier == nil {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		token, err := s.store.PushToken(ctx, mailbox)
		if err != nil || token == "" {
			return
		}
		if err := s.notifier.Notify(ctx, token); err != nil {
			s.logger.Warn("push wake failed")
		}
	}()
}

func (s *Server) respondError(w http.ResponseWriter, err error) {
	status, code := http.StatusInternalServerError, "internal_error"
	switch {
	case errors.Is(err, ErrUnauthorized):
		status, code = http.StatusUnauthorized, "unauthorized"
	case errors.Is(err, ErrNotFound):
		status, code = http.StatusNotFound, "not_found"
	case errors.Is(err, ErrConflict):
		status, code = http.StatusConflict, "conflict"
	case errors.Is(err, ErrLimit), errors.Is(err, ErrBootstrapConsumed):
		status, code = http.StatusInsufficientStorage, "capacity_reached"
	default:
		s.logger.Error("relay request failed", "error", err.Error())
	}
	writeJSON(w, status, map[string]string{"error": code})
}

func (s *Server) middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, request *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Content-Security-Policy", "default-src 'none'")
		w.Header().Set("Referrer-Policy", "no-referrer")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		if request.URL.Path != "/healthz" && !s.limiter.Allow(clientAddress(request)) {
			writeJSON(w, http.StatusTooManyRequests, map[string]string{"error": "rate_limited"})
			return
		}
		started := time.Now()
		recorder := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		defer func() {
			if recovered := recover(); recovered != nil {
				s.logger.Error("relay panic recovered")
				writeJSON(recorder, http.StatusInternalServerError, map[string]string{"error": "internal_error"})
			}
			s.logger.Info(
				"request completed",
				"method", request.Method,
				"status", recorder.status,
				"duration_ms", time.Since(started).Milliseconds(),
			)
		}()
		next.ServeHTTP(recorder, request)
	})
}

func bearerToken(request *http.Request) string {
	header := request.Header.Get("Authorization")
	if !strings.HasPrefix(header, "Bearer ") {
		return ""
	}
	return strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
}

func validObjectPath(mailbox, object string) bool {
	return opaqueID.MatchString(mailbox) && opaqueID.MatchString(object)
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeOpaqueFile(w http.ResponseWriter, reader io.Reader, size int64) {
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Length", fmt.Sprintf("%d", size))
	w.WriteHeader(http.StatusOK)
	_, _ = io.Copy(w, reader)
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (r *statusRecorder) WriteHeader(status int) {
	r.status = status
	r.ResponseWriter.WriteHeader(status)
}

type fixedWindowLimiter struct {
	mu       sync.Mutex
	limit    int
	duration time.Duration
	entries  map[string]windowEntry
}

type windowEntry struct {
	started time.Time
	count   int
}

func newFixedWindowLimiter(limit int, duration time.Duration) *fixedWindowLimiter {
	return &fixedWindowLimiter{limit: limit, duration: duration, entries: make(map[string]windowEntry)}
}

func (l *fixedWindowLimiter) Allow(key string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now()
	entry := l.entries[key]
	if entry.started.IsZero() || now.Sub(entry.started) >= l.duration {
		l.entries[key] = windowEntry{started: now, count: 1}
		return true
	}
	if entry.count >= l.limit {
		return false
	}
	entry.count++
	l.entries[key] = entry
	return true
}

func clientAddress(request *http.Request) string {
	host, _, err := net.SplitHostPort(request.RemoteAddr)
	if err != nil {
		return request.RemoteAddr
	}
	return host
}
