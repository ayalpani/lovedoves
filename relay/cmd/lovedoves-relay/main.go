package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/ayalpani/lovedoves/relay/internal/relay"
)

func main() {
	if len(os.Args) == 2 && os.Args[1] == "bootstrap-token" {
		token, err := relay.RandomToken(32)
		if err != nil {
			slog.Error("could not generate bootstrap token")
			os.Exit(1)
		}
		_, _ = os.Stdout.WriteString(token + "\n")
		return
	}
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	bootstrapToken := os.Getenv("LOVE_DOVES_BOOTSTRAP_TOKEN")
	dataDirectory := envOr("LOVE_DOVES_DATA_DIR", "./data")
	store, err := relay.OpenStore(dataDirectory)
	if err != nil {
		logger.Error("could not open relay store", "error", err.Error())
		os.Exit(1)
	}
	defer store.Close()

	var notifier relay.Notifier
	projectID, credentials := os.Getenv("FCM_PROJECT_ID"), os.Getenv("GOOGLE_APPLICATION_CREDENTIALS")
	if projectID != "" && credentials != "" {
		notifier, err = relay.NewFCMNotifier(projectID, credentials)
		if err != nil {
			logger.Error("could not configure FCM", "error", err.Error())
			os.Exit(1)
		}
	}
	handler := relay.NewServer(
		store,
		bootstrapToken,
		notifier,
		logger,
		os.Getenv("LOVE_DOVES_ADMIN_EMAIL"),
	).Handler()
	server := &http.Server{
		Addr:              envOr("LOVE_DOVES_LISTEN_ADDR", ":8787"),
		Handler:           handler,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       45 * time.Second,
		WriteTimeout:      45 * time.Second,
		IdleTimeout:       60 * time.Second,
		MaxHeaderBytes:    16 * 1024,
	}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go cleanup(ctx, store, logger)
	go func() {
		<-ctx.Done()
		shutdownContext, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = server.Shutdown(shutdownContext)
	}()
	logger.Info("relay listening", "address", server.Addr, "fcm", notifier != nil)
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		logger.Error("relay stopped unexpectedly", "error", err.Error())
		os.Exit(1)
	}
}

func cleanup(ctx context.Context, store *relay.Store, logger *slog.Logger) {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			if err := store.CleanupExpired(ctx); err != nil {
				logger.Error("expiry cleanup failed", "error", err.Error())
			}
		}
	}
}

func envOr(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}
