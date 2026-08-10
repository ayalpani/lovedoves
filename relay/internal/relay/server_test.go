package relay

import (
	"bytes"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
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
	server, _ := testServer(t)
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
}

func testServer(t *testing.T) (http.Handler, *Store) {
	t.Helper()
	store, err := OpenStore(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = store.Close() })
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	return NewServer(store, "bootstrap-secret", nil, logger).Handler(), store
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
