package relay

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
)

type FCMNotifier struct {
	projectID  string
	tokens     oauth2.TokenSource
	httpClient *http.Client
}

func NewFCMNotifier(projectID, credentialsPath string) (*FCMNotifier, error) {
	credentialsJSON, err := os.ReadFile(credentialsPath)
	if err != nil {
		return nil, fmt.Errorf("read FCM credentials: %w", err)
	}
	credentials, err := google.CredentialsFromJSON(
		context.Background(),
		credentialsJSON,
		"https://www.googleapis.com/auth/firebase.messaging",
	)
	if err != nil {
		return nil, fmt.Errorf("parse FCM credentials: %w", err)
	}
	return &FCMNotifier{
		projectID:  projectID,
		tokens:     oauth2.ReuseTokenSource(nil, credentials.TokenSource),
		httpClient: &http.Client{},
	}, nil
}

func (n *FCMNotifier) Notify(ctx context.Context, deviceToken string) error {
	payload := map[string]any{
		"message": map[string]any{
			"fid":  deviceToken,
			"data": map[string]string{"wake": "1"},
			"android": map[string]string{
				"priority": "HIGH",
			},
		},
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	token, err := n.tokens.Token()
	if err != nil {
		return fmt.Errorf("obtain FCM access token: %w", err)
	}
	endpoint := "https://fcm.googleapis.com/v1/projects/" + url.PathEscape(n.projectID) + "/messages:send"
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(body))
	if err != nil {
		return err
	}
	request.Header.Set("Authorization", "Bearer "+token.AccessToken)
	request.Header.Set("Content-Type", "application/json")
	response, err := n.httpClient.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode/100 != 2 {
		_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 4096))
		return fmt.Errorf("FCM returned status %d", response.StatusCode)
	}
	return nil
}
