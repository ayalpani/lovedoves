package relay

import (
	"bytes"
	"fmt"
	"html/template"
	"net/http"
	"runtime/debug"
	"strings"
)

type adminPageData struct {
	MailboxCount    int
	ObjectCount     int
	ObjectLabel     string
	ObjectBytes     string
	RendezvousCount int
	FCMLabel        string
	FCMClass        string
	BuildVersion    string
	StatusLabel     string
}

func (s *Server) admin(w http.ResponseWriter, request *http.Request) {
	if !s.adminAuthorized(request) {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "unauthorized"})
		return
	}
	stats, err := s.store.AdminStats(request.Context())
	if err != nil {
		s.respondError(w, err)
		return
	}
	data := adminPageData{
		MailboxCount:    stats.MailboxCount,
		ObjectCount:     stats.ObjectCount,
		ObjectLabel:     "Objekte",
		ObjectBytes:     formatAdminBytes(stats.ObjectBytes),
		RendezvousCount: stats.RendezvousCount,
		FCMLabel:        "Nicht konfiguriert",
		FCMClass:        "status-dot--muted",
		BuildVersion:    relayBuildVersion(),
		StatusLabel:     "Relay läuft",
	}
	if stats.ObjectCount == 1 {
		data.ObjectLabel = "Objekt"
	}
	if s.notifier != nil {
		data.FCMLabel = "Aktiv"
		data.FCMClass = "status-dot--ok"
		data.StatusLabel = "Alles in Ordnung"
	}
	var body bytes.Buffer
	if err := adminTemplate.Execute(&body, data); err != nil {
		s.respondError(w, err)
		return
	}
	setAdminHeaders(w)
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(body.Bytes())
}

func (s *Server) adminStyles(w http.ResponseWriter, request *http.Request) {
	if !s.adminAuthorized(request) {
		writeJSON(w, http.StatusUnauthorized, map[string]string{"error": "unauthorized"})
		return
	}
	setAdminHeaders(w)
	w.Header().Set("Content-Type", "text/css; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(adminCSS))
}

func (s *Server) adminAuthorized(request *http.Request) bool {
	return s.adminEmail != "" && strings.EqualFold(
		strings.TrimSpace(request.Header.Get("X-Love-Doves-Admin-Email")),
		s.adminEmail,
	)
}

func setAdminHeaders(w http.ResponseWriter) {
	w.Header().Set(
		"Content-Security-Policy",
		"default-src 'none'; style-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
	)
	w.Header().Set("X-Frame-Options", "DENY")
}

func formatAdminBytes(byteCount int64) string {
	units := []string{"B", "KB", "MB", "GB"}
	value, unit := float64(byteCount), 0
	for value >= 1024 && unit < len(units)-1 {
		value /= 1024
		unit++
	}
	if unit == 0 {
		return fmt.Sprintf("%d B", byteCount)
	}
	return strings.Replace(fmt.Sprintf("%.1f %s", value, units[unit]), ".", ",", 1)
}

func relayBuildVersion() string {
	info, ok := debug.ReadBuildInfo()
	if !ok {
		return "dev"
	}
	if info.Main.Version != "" && info.Main.Version != "(devel)" {
		return strings.TrimPrefix(info.Main.Version, "v")
	}
	for _, setting := range info.Settings {
		if setting.Key == "vcs.revision" && len(setting.Value) >= 8 {
			return setting.Value[:8]
		}
	}
	return "dev"
}

var adminTemplate = template.Must(template.New("admin").Parse(adminHTML))

const adminHTML = `<!doctype html>
<html lang="de">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light">
  <title>Love Doves · Relay Admin</title>
  <link rel="stylesheet" href="/admin/styles.css">
</head>
<body>
  <div class="admin-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M12 21s-7-4.35-9.5-8.5C.4 9 2.3 5 6.3 5c2.2 0 3.6 1.2 4.7 2.7C12.1 6.2 13.5 5 15.7 5c4 0 5.9 4 3.8 7.5C17 16.65 12 21 12 21Z"/></svg>
        </span>
        <span class="brand-name">Love Doves</span>
        <span class="brand-subtitle">Relay Admin</span>
      </div>
      <div class="topbar-status">
        <span class="status-dot status-dot--ok" aria-hidden="true"></span>
        <span>{{.StatusLabel}}</span>
        <span class="admin-label">Admin</span>
      </div>
    </header>

    <main class="content">
      <section class="primary-column">
        <div class="hero">
          <p class="eyebrow">Relay · heute</p>
          <h1>Alles läuft.<br>Nichts wird mitgelesen.</h1>
          <p class="hero-copy">Der Relay transportiert ausschließlich verschlüsselte Objekte. Inhalte, Schlüssel und Partnernamen bleiben außerhalb dieses Systems.</p>
        </div>

        <div class="metric-row">
          <section class="metric metric--mailboxes">
            <div class="metric-heading">
              <span>Verbundene Geräte</span>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 13a5 5 0 0 0 7.5.5l2-2a5 5 0 0 0-7-7l-1.1 1.1"/><path d="M14 11a5 5 0 0 0-7.5-.5l-2 2a5 5 0 0 0 7 7l1.1-1.1"/></svg>
            </div>
            <div class="metric-number-line"><strong>{{.MailboxCount}}</strong><span>von 2 aktiv</span></div>
          </section>

          <section class="metric metric--queue">
            <div class="metric-heading">Warteschlange</div>
            <div><strong>{{.ObjectCount}}</strong><span>{{.ObjectLabel}} · {{.ObjectBytes}}</span></div>
          </section>
        </div>

        <section class="limits" aria-label="Betriebsgrenzen">
          <div><span>Mailbox-Limit</span><strong>1 GB</strong></div>
          <div><span>Max. Aufbewahrung</span><strong>72 Stunden</strong></div>
          <div><span>Access-Log</span><strong>Aus</strong></div>
        </section>
      </section>

      <aside class="context-column">
        <section class="privacy-note">
          <div class="privacy-heading">
            <span>Datenschutz</span>
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m2 2 20 20"/><path d="M6.7 6.7C4.7 8 3.3 9.8 2.5 12c1.7 4.5 5.2 7 9.5 7 1.5 0 2.8-.3 4-.8"/><path d="M10.7 5.1A10.5 10.5 0 0 1 12 5c4.3 0 7.8 2.5 9.5 7a11.8 11.8 0 0 1-2.2 3.5"/><path d="M14.1 14.1a3 3 0 0 1-4.2-4.2"/></svg>
          </div>
          <div class="privacy-copy"><h2>Blind, mit Absicht.</h2><p>Diese Oberfläche zeigt Betriebszustände – niemals Nachrichten, Medien oder Beziehungen.</p></div>
          <p class="privacy-footnote">Keine Vorschauen · Keine Namen · Keine Schlüssel</p>
        </section>

        <section class="system-card">
          <div class="system-heading"><h2>System</h2><span>Live</span></div>
          <dl class="system-rows">
            <div><dt>FCM-Wecksignal</dt><dd><span class="status-dot {{.FCMClass}}" aria-hidden="true"></span>{{.FCMLabel}}</dd></div>
            <div><dt>Aktive Rendezvous</dt><dd>{{.RendezvousCount}}</dd></div>
            <div><dt>Ablaufbereinigung</dt><dd>jede Minute</dd></div>
            <div><dt>Relay-Version</dt><dd>{{.BuildVersion}}</dd></div>
          </dl>
        </section>
      </aside>
    </main>
  </div>
</body>
</html>`

const adminCSS = `:root {
  --paper: #f7f5f0;
  --surface: #ffffff;
  --ink: #18201c;
  --mist: #e6e9ec;
  --blush: #f7dfe4;
  --muted: #53605a;
  --soft-muted: #66706a;
  --success: #3b7d5a;
  --warning: #9b772f;
  --radius: 22px;
}

* { box-sizing: border-box; }

html { background: var(--paper); }

body {
  margin: 0;
  min-width: 320px;
  background: var(--paper);
  color: var(--ink);
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-size: 15px;
  line-height: 22px;
  -webkit-font-smoothing: antialiased;
}

.admin-shell {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 1440px;
  min-height: 100vh;
  margin: 0 auto;
}

.topbar {
  display: flex;
  flex: 0 0 112px;
  align-items: center;
  justify-content: space-between;
  padding: 0 64px;
  border-bottom: 1px solid var(--mist);
}

.brand, .topbar-status, .metric-heading, .privacy-heading, .system-heading,
.metric-number-line, .system-rows div, .system-rows dd {
  display: flex;
  align-items: center;
}

.brand { gap: 10px; }
.brand-mark {
  display: flex;
  width: 36px;
  height: 36px;
  align-items: center;
  justify-content: center;
  margin-right: 4px;
  border-radius: 50%;
  background: var(--blush);
}
.brand-mark svg { width: 18px; height: 18px; fill: var(--ink); }
.brand-name { font-size: 18px; font-weight: 650; letter-spacing: -.03em; }
.brand-subtitle, .admin-label { color: var(--soft-muted); font-size: 13px; }
.topbar-status { gap: 9px; font-size: 13px; font-weight: 500; }
.admin-label { margin-left: 19px; font-weight: 400; }

.status-dot { display: inline-block; width: 8px; height: 8px; flex: 0 0 8px; border-radius: 50%; }
.status-dot--ok { background: var(--success); }
.status-dot--muted { background: var(--warning); }

.content {
  display: flex;
  flex: 1;
  gap: 48px;
  padding: 48px 64px 64px;
}

.primary-column {
  display: flex;
  width: 832px;
  flex: 0 0 832px;
  flex-direction: column;
  gap: 32px;
}

.hero { padding: 8px 0 16px; }
.eyebrow, .metric-heading, .privacy-heading {
  margin: 0;
  color: var(--soft-muted);
  font-size: 13px;
  font-weight: 500;
  letter-spacing: .08em;
  line-height: 18px;
  text-transform: uppercase;
}
.hero h1 {
  margin: 16px 0;
  color: #000;
  font-size: 56px;
  font-weight: 650;
  letter-spacing: -.03em;
  line-height: 60px;
}
.hero-copy { width: 620px; margin: 0; color: var(--muted); }

.metric-row { display: flex; height: 224px; gap: 16px; }
.metric {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 32px;
  border-radius: var(--radius);
}
.metric-heading { justify-content: space-between; color: var(--soft-muted); }
.metric-heading svg {
  width: 22px;
  height: 22px;
  fill: none;
  stroke: var(--ink);
  stroke-width: 1.7;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.metric--mailboxes { width: 512px; flex: 0 0 512px; background: var(--blush); }
.metric--mailboxes .metric-heading { color: var(--ink); }
.metric--mailboxes strong { color: #000; font-size: 72px; font-weight: 650; letter-spacing: -.03em; line-height: 70px; }
.metric-number-line { align-items: flex-end; gap: 12px; }
.metric-number-line span { padding-bottom: 8px; color: var(--muted); font-size: 18px; line-height: 26px; }
.metric--queue { flex: 1; border: 1px solid var(--mist); background: var(--surface); }
.metric--queue > div:last-child { display: flex; flex-direction: column; gap: 2px; }
.metric--queue strong { color: #000; font-size: 48px; font-weight: 650; letter-spacing: -.03em; line-height: 52px; }
.metric--queue span { color: var(--muted); }

.limits {
  display: flex;
  height: 86px;
  align-items: center;
  border-top: 1px solid var(--mist);
  border-bottom: 1px solid var(--mist);
}
.limits div { display: flex; flex: 1; flex-direction: column; gap: 4px; }
.limits div + div { padding-left: 24px; border-left: 1px solid var(--mist); }
.limits span { color: var(--soft-muted); font-size: 13px; line-height: 18px; }
.limits strong { font-size: 18px; font-weight: 650; line-height: 26px; }

.context-column { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 24px; }
.privacy-note {
  display: flex;
  height: 344px;
  flex: 0 0 344px;
  flex-direction: column;
  justify-content: space-between;
  padding: 32px;
  border-radius: var(--radius);
  background: var(--ink);
  color: var(--paper);
}
.privacy-heading { justify-content: space-between; color: #b8c0bb; }
.privacy-heading svg {
  width: 24px;
  height: 24px;
  fill: none;
  stroke: var(--paper);
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.privacy-copy h2 { margin: 0 0 16px; font-size: 32px; font-weight: 650; letter-spacing: -.03em; line-height: 38px; }
.privacy-copy p { margin: 0; color: #c9cfcc; }
.privacy-footnote { margin: 0; padding-top: 16px; border-top: 1px solid #3a423e; color: #aeb7b2; font-size: 13px; line-height: 18px; }

.system-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
  border: 1px solid var(--mist);
  border-radius: var(--radius);
  background: var(--surface);
}
.system-heading { justify-content: space-between; }
.system-heading h2 { margin: 0; font-size: 18px; font-weight: 650; letter-spacing: -.03em; line-height: 26px; }
.system-heading span { color: var(--soft-muted); font-size: 13px; line-height: 18px; }
.system-rows { margin: 0; }
.system-rows div { height: 48px; justify-content: space-between; border-bottom: 1px solid var(--mist); }
.system-rows div:last-child { border-bottom: 0; }
.system-rows dt, .system-rows dd { margin: 0; }
.system-rows dd { width: 152px; flex: 0 0 152px; justify-content: flex-end; gap: 8px; color: var(--muted); font-size: 13px; text-align: right; }

@media (max-width: 1120px) {
  .content { flex-direction: column; }
  .primary-column { width: 100%; flex-basis: auto; }
  .context-column { flex-direction: row; }
  .privacy-note, .system-card { min-height: 344px; flex: 1; }
}

@media (max-width: 720px) {
  .topbar { flex-basis: 88px; padding: 0 24px; }
  .brand-subtitle, .admin-label { display: none; }
  .content { gap: 32px; padding: 32px 24px 48px; }
  .hero h1 { font-size: 42px; line-height: 46px; }
  .hero-copy { width: auto; }
  .metric-row { height: auto; flex-direction: column; }
  .metric--mailboxes, .metric--queue { width: 100%; min-height: 200px; flex-basis: auto; }
  .limits { height: auto; align-items: stretch; flex-direction: column; }
  .limits div { padding: 16px 0; }
  .limits div + div { padding-left: 0; border-top: 1px solid var(--mist); border-left: 0; }
  .context-column { flex-direction: column; }
  .privacy-note { min-height: 344px; }
  .system-card { min-height: 292px; }
  .system-rows dd { width: 132px; flex-basis: 132px; }
}`
