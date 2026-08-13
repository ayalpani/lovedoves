package relay

import (
	"bytes"
	"context"
	"fmt"
	"html/template"
	"math"
	"net/http"
	"sort"
	"strings"
	"time"
)

type adminPageData struct {
	AdminEmail       string
	MailboxCount     int
	ObjectCount      int
	ObjectDetail     string
	ObjectBytes      string
	RendezvousCount  int
	RendezvousTitle  string
	RendezvousDetail string
	FCMLabel         string
	FCMDetail        string
	FCMClass         string
	TopStatusClass   string
	StatusLabel      string
	CheckTime        string
	CheckSeconds     string
	CheckSummary     string
	Chart            adminChartData
}

type adminSnapshot struct {
	At    time.Time
	Stats AdminStats
}

type adminChartPoint struct {
	X string
	Y string
}

type adminChartData struct {
	YMax          int
	YMid          int
	QueuePoints   string
	QueueArea     string
	MailboxPoints string
	QueueDots     []adminChartPoint
	MailboxDots   []adminChartPoint
	ObjectPeak    int
	DeviceRange   string
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
	now := s.store.now()
	s.recordAdminSnapshot(stats, now)
	data := adminPageData{
		AdminEmail:       strings.TrimSpace(request.Header.Get("X-Love-Doves-Admin-Email")),
		MailboxCount:     stats.MailboxCount,
		ObjectCount:      stats.ObjectCount,
		ObjectBytes:      formatAdminBytes(stats.ObjectBytes),
		RendezvousCount:  stats.RendezvousCount,
		RendezvousTitle:  "Übergaben ruhig",
		RendezvousDetail: fmt.Sprintf("%d aktive Rendezvous", stats.RendezvousCount),
		FCMLabel:         "Wecksignal fehlt",
		FCMDetail:        "FCM ist nicht eingerichtet",
		FCMClass:         "status-dot--muted",
		TopStatusClass:   "status-dot--muted",
		StatusLabel:      "Relay läuft",
		CheckTime:        now.Format("15:04"),
		CheckSeconds:     now.Format(":05"),
		CheckSummary:     "Relay und Speicher wurden geprüft; FCM ist nicht eingerichtet.",
		Chart:            s.adminChart(stats, now),
	}
	objectLabel := "verschlüsselte Objekte"
	if stats.ObjectCount == 1 {
		objectLabel = "verschlüsseltes Objekt"
	}
	data.ObjectDetail = fmt.Sprintf("%d %s · %s", stats.ObjectCount, objectLabel, data.ObjectBytes)
	if stats.RendezvousCount > 0 {
		data.RendezvousTitle = "Übergaben aktiv"
	}
	if s.notifier != nil {
		data.FCMLabel = "Wecksignal bereit"
		data.FCMDetail = "FCM nimmt Weckaufträge an"
		data.FCMClass = "status-dot--ok"
		data.TopStatusClass = "status-dot--ok"
		data.StatusLabel = "Alles in Ordnung"
		data.CheckSummary = "Diese Prüfungen bestätigen den gesunden Betrieb."
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

func (s *Server) captureAdminSnapshot(ctx context.Context) {
	stats, err := s.store.AdminStats(ctx)
	if err == nil {
		s.recordAdminSnapshot(stats, s.store.now())
	}
}

func (s *Server) recordAdminSnapshot(stats AdminStats, at time.Time) {
	s.adminHistoryMu.Lock()
	defer s.adminHistoryMu.Unlock()

	if len(s.adminHistory) == 0 || s.adminHistory[len(s.adminHistory)-1].Stats != stats {
		s.adminHistory = append(s.adminHistory, adminSnapshot{At: at, Stats: stats})
	}
	cutoff := at.Add(-24 * time.Hour)
	first := sort.Search(len(s.adminHistory), func(i int) bool {
		return !s.adminHistory[i].At.Before(cutoff)
	})
	if first > 1 {
		first--
		s.adminHistory = append([]adminSnapshot(nil), s.adminHistory[first:]...)
	}
	if len(s.adminHistory) > 1024 {
		s.adminHistory = append([]adminSnapshot(nil), s.adminHistory[len(s.adminHistory)-1024:]...)
	}
}

func (s *Server) adminChart(current AdminStats, now time.Time) adminChartData {
	s.adminHistoryMu.Lock()
	snapshots := append([]adminSnapshot(nil), s.adminHistory...)
	s.adminHistoryMu.Unlock()
	if len(snapshots) == 0 || snapshots[len(snapshots)-1].At.Before(now) {
		snapshots = append(snapshots, adminSnapshot{At: now, Stats: current})
	}

	cutoff := now.Add(-24 * time.Hour)
	start := sort.Search(len(snapshots), func(i int) bool {
		return !snapshots[i].At.Before(cutoff)
	})
	if start > 0 {
		start--
	}
	snapshots = snapshots[start:]
	if len(snapshots) > 49 {
		compacted := make([]adminSnapshot, 0, 49)
		for i := 0; i < 49; i++ {
			index := int(math.Round(float64(i) * float64(len(snapshots)-1) / 48))
			compacted = append(compacted, snapshots[index])
		}
		snapshots = compacted
	}

	yMax := 6
	objectPeak := 0
	deviceMin, deviceMax := current.MailboxCount, current.MailboxCount
	for _, snapshot := range snapshots {
		yMax = max(yMax, snapshot.Stats.ObjectCount, snapshot.Stats.MailboxCount)
		objectPeak = max(objectPeak, snapshot.Stats.ObjectCount)
		deviceMin = min(deviceMin, snapshot.Stats.MailboxCount)
		deviceMax = max(deviceMax, snapshot.Stats.MailboxCount)
	}

	queueDots := make([]adminChartPoint, 0, len(snapshots))
	mailboxDots := make([]adminChartPoint, 0, len(snapshots))
	queueParts := make([]string, 0, len(snapshots))
	mailboxParts := make([]string, 0, len(snapshots))
	for _, snapshot := range snapshots {
		x := math.Max(0, math.Min(718, 718-now.Sub(snapshot.At).Hours()/24*718))
		queueY := 124 - float64(snapshot.Stats.ObjectCount)/float64(yMax)*114
		mailboxY := 124 - float64(snapshot.Stats.MailboxCount)/float64(yMax)*114
		queuePoint := adminChartPoint{X: formatChartCoordinate(x), Y: formatChartCoordinate(queueY)}
		mailboxPoint := adminChartPoint{X: formatChartCoordinate(x), Y: formatChartCoordinate(mailboxY)}
		queueDots = append(queueDots, queuePoint)
		mailboxDots = append(mailboxDots, mailboxPoint)
		queueParts = append(queueParts, queuePoint.X+","+queuePoint.Y)
		mailboxParts = append(mailboxParts, mailboxPoint.X+","+mailboxPoint.Y)
	}
	queueArea := ""
	if len(queueDots) > 0 {
		queueArea = queueDots[0].X + ",124 " + strings.Join(queueParts, " ") + " " + queueDots[len(queueDots)-1].X + ",124"
	}
	deviceRange := fmt.Sprintf("%d", deviceMin)
	if deviceMin != deviceMax {
		deviceRange = fmt.Sprintf("%d–%d", deviceMin, deviceMax)
	}
	return adminChartData{
		YMax:          yMax,
		YMid:          yMax / 2,
		QueuePoints:   strings.Join(queueParts, " "),
		QueueArea:     queueArea,
		MailboxPoints: strings.Join(mailboxParts, " "),
		QueueDots:     queueDots,
		MailboxDots:   mailboxDots,
		ObjectPeak:    objectPeak,
		DeviceRange:   deviceRange,
	}
}

func formatChartCoordinate(value float64) string {
	return strings.TrimSuffix(strings.TrimSuffix(fmt.Sprintf("%.1f", value), "0"), ".")
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
      <div class="account">
        <span class="account-status"><span class="status-dot {{.TopStatusClass}}" aria-hidden="true"></span>{{.StatusLabel}}</span>
        <a class="account-email" href="/oauth2/sign_out" aria-label="Abmelden: {{.AdminEmail}}" title="Abmelden">{{.AdminEmail}}</a>
      </div>
    </header>

    <main class="content">
      <section class="primary-column">
        <div class="hero">
          <p class="mobile-only">Relay · heute</p>
          <h1><span class="hero-title-line">Alles läuft.</span><span class="hero-title-line">Nichts wird mitgelesen.</span></h1>
          <p class="hero-copy"><span class="desktop-only">Der Relay transportiert ausschließlich verschlüsselte Objekte. Inhalte, Schlüssel und Partnernamen bleiben außerhalb dieses Systems.</span><span class="mobile-only">Der Relay transportiert ausschließlich verschlüsselte Objekte.</span></p>
        </div>

        <section class="metrics" aria-label="Aktueller Relay-Zustand">
          <div class="metric">
            <span class="metric-value">{{.MailboxCount}}</span>
            <span class="metric-label">Geräte verbunden</span>
            <span class="metric-detail">von 2</span>
          </div>
          <div class="metric">
            <span class="metric-value">{{.ObjectCount}}</span>
            <span class="metric-label">Objekte warten</span>
            <span class="metric-detail">{{.ObjectBytes}}</span>
          </div>
          <div class="metric">
            <span class="metric-value">{{.RendezvousCount}}</span>
            <span class="metric-label">Übergaben aktiv</span>
            <span class="metric-detail"><span class="desktop-only">im Moment</span><span class="mobile-only">jetzt</span></span>
          </div>
        </section>

        <section class="chart-card" aria-labelledby="chart-title">
          <div class="chart-header">
            <div><span id="chart-title" class="section-title">Verlauf</span><span class="section-detail desktop-only">letzte 24 Stunden</span><span class="section-detail mobile-only">24 Stunden</span></div>
            <div class="chart-legend">
              <span><span class="status-dot status-dot--ok" aria-hidden="true"></span>Geräte</span>
              <span><span class="status-dot status-dot--ink" aria-hidden="true"></span><span class="desktop-only">Warteschlange</span><span class="mobile-only">Objekte</span></span>
            </div>
          </div>
          <div class="chart-body">
            <div class="chart-axis"><span>{{.Chart.YMax}}</span><span class="chart-mid">{{.Chart.YMid}}</span><span>0</span></div>
            <svg class="chart" viewBox="0 0 718 136" role="img" aria-label="Verlauf von verbundenen Geräten und wartenden Objekten seit dem letzten Relay-Start, maximal 24 Stunden">
              <line x1="0" y1="10" x2="718" y2="10"/>
              <line x1="0" y1="67" x2="718" y2="67"/>
              <line x1="0" y1="124" x2="718" y2="124"/>
              {{if .Chart.QueueArea}}<polygon class="chart-area" points="{{.Chart.QueueArea}}"/>{{end}}
              <polyline class="chart-queue" points="{{.Chart.QueuePoints}}"/>
              <polyline class="chart-mailboxes" points="{{.Chart.MailboxPoints}}"/>
              {{range .Chart.QueueDots}}<circle class="chart-queue-dot" cx="{{.X}}" cy="{{.Y}}" r="4"/>{{end}}
              {{range .Chart.MailboxDots}}<circle class="chart-mailbox-dot" cx="{{.X}}" cy="{{.Y}}" r="4"/>{{end}}
            </svg>
          </div>
          <div class="chart-times"><span>00</span><span class="desktop-only">06</span><span>12</span><span class="desktop-only">18</span><span>jetzt</span></div>
          <div class="chart-footer">
            <span>Spitze: {{.Chart.ObjectPeak}}<span class="desktop-only"> Objekte</span></span>
            <span class="desktop-only">Geräte: {{.Chart.DeviceRange}}</span>
            <span>Aktuell: {{.ObjectBytes}}</span>
          </div>
        </section>
      </section>

      <aside class="context-column">
        <section class="protocol-card" aria-labelledby="protocol-title">
          <div class="protocol-header"><span id="protocol-title" class="section-title">Betriebsprotokoll</span><span class="live mobile-only"><span class="status-dot status-dot--ok" aria-hidden="true"></span>live</span></div>
          <div class="protocol-intro"><h2>Gerade geprüft.</h2><p>{{.CheckSummary}}</p></div>
          <div class="protocol-row">
            <time datetime="{{.CheckTime}}{{.CheckSeconds}}">{{.CheckTime}}<span class="desktop-only">{{.CheckSeconds}}</span></time>
            <span class="status-dot status-dot--ok" aria-hidden="true"></span>
            <div><span class="protocol-title">Speicher geprüft</span><span class="protocol-detail">{{.ObjectDetail}}</span></div>
          </div>
          <div class="protocol-row">
            <time datetime="{{.CheckTime}}{{.CheckSeconds}}">{{.CheckTime}}<span class="desktop-only">{{.CheckSeconds}}</span></time>
            <span class="status-dot {{.FCMClass}}" aria-hidden="true"></span>
            <div><span class="protocol-title">{{.FCMLabel}}</span><span class="protocol-detail">{{.FCMDetail}}</span></div>
          </div>
          <div class="protocol-row">
            <time datetime="{{.CheckTime}}{{.CheckSeconds}}">{{.CheckTime}}<span class="desktop-only">{{.CheckSeconds}}</span></time>
            <span class="status-dot status-dot--ok" aria-hidden="true"></span>
            <div><span class="protocol-title">{{.RendezvousTitle}}</span><span class="protocol-detail">{{.RendezvousDetail}}</span></div>
          </div>
        </section>

        <section class="privacy-note">
          <div class="privacy-heading"><span>Der Relay kann keine Inhalte lesen</span><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m2 2 20 20"/><path d="M6.7 6.7C4.7 8 3.3 9.8 2.5 12c1.7 4.5 5.2 7 9.5 7 1.5 0 2.8-.3 4-.8"/></svg></div>
          <p>Das Protokoll zeigt nur, ob Transport, Zustellung und Löschung funktionieren.</p>
          <p class="privacy-footnote">Keine Nachrichten · keine Namen · keine Schlüssel</p>
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

* { box-sizing: border-box; font-weight: 400; }
html { background: var(--paper); }
body {
  margin: 0;
  min-width: 320px;
  background: var(--paper);
  color: var(--ink);
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-size: 14px;
  line-height: 22px;
  -webkit-font-smoothing: antialiased;
}
a { color: inherit; }
.admin-shell { width: 100%; max-width: 1440px; min-height: 100vh; margin: 0 auto; }

.topbar {
  display: flex;
  height: 112px;
  align-items: center;
  justify-content: space-between;
  padding: 0 64px;
  border-bottom: 1px solid var(--mist);
}
.brand, .account, .account-status, .metric, .chart-header > div, .chart-legend,
.chart-legend > span, .protocol-header, .live, .privacy-heading {
  display: flex;
  align-items: center;
}
.mobile-only { display: none; }
.brand { gap: 10px; }
.brand-mark {
  display: flex;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  margin-right: 4px;
  border-radius: 50%;
  background: var(--blush);
}
.brand-mark svg { width: 18px; height: 18px; fill: var(--ink); }
.brand-name { color: #000; font-size: 18px; line-height: 26px; }
.brand-subtitle { color: var(--soft-muted); }
.account { gap: 28px; }
.account-status { order: 1; gap: 9px; color: #000; }
.account-email {
  order: 2;
  color: var(--soft-muted);
  text-decoration: underline;
  text-decoration-color: transparent;
  text-underline-offset: 4px;
}
.account-email:hover, .account-email:focus-visible { color: var(--ink); text-decoration-color: currentColor; }
.status-dot { display: inline-block; width: 8px; height: 8px; flex: 0 0 8px; border-radius: 50%; }
.status-dot--ok { background: var(--success); }
.status-dot--muted { background: var(--warning); }
.status-dot--ink { background: var(--ink); }

.content { display: flex; gap: 32px; padding: 48px 64px 64px; }
.primary-column { display: flex; width: 824px; flex: 0 0 824px; flex-direction: column; gap: 32px; }
.context-column { display: flex; width: 456px; flex: 0 0 456px; flex-direction: column; gap: 24px; }

.hero { display: flex; min-height: 130px; flex-direction: column; gap: 16px; padding: 8px 0 16px; }
.hero h1, .protocol-intro h2 {
  margin: 0;
  color: #000;
  font-size: 42px;
  letter-spacing: -.02em;
  line-height: 46px;
}
.hero-title-line + .hero-title-line::before { content: " "; }
.hero-copy { width: 620px; margin: 0; color: var(--muted); }

.metrics {
  display: flex;
  height: 108px;
  flex: 0 0 108px;
  align-items: center;
  padding: 20px 24px;
  border-radius: var(--radius);
  background: var(--blush);
}
.metric { width: 258px; flex: 0 0 258px; gap: 12px; padding-right: 22px; }
.metric + .metric { padding-right: 22px; padding-left: 22px; border-left: 1px solid #18201c24; }
.metric:last-child { padding-right: 0; }
.metric-value { color: var(--ink); font-size: 42px; letter-spacing: -.02em; line-height: 46px; }
.metric-label, .section-title, .protocol-title { font-size: 18px; line-height: 26px; }
.metric-label { white-space: nowrap; }
.metric-detail { color: var(--muted); }

.chart-card, .protocol-card {
  border: 1px solid var(--mist);
  border-radius: var(--radius);
  background: var(--surface);
}
.chart-card { height: 328px; flex: 0 0 328px; padding: 24px; }
.chart-header { display: flex; height: 26px; align-items: center; justify-content: space-between; }
.chart-header > div:first-child { align-items: baseline; gap: 10px; }
.section-detail, .chart-legend { color: var(--soft-muted); }
.chart-legend { gap: 18px; }
.chart-legend > span { gap: 7px; }
.chart-body { display: flex; height: 150px; gap: 10px; padding-top: 14px; }
.chart-axis {
  display: flex;
  width: 24px;
  height: 136px;
  flex: 0 0 24px;
  flex-direction: column;
  justify-content: space-between;
  color: var(--soft-muted);
}
.chart { width: 718px; height: 136px; overflow: visible; }
.chart line { stroke: var(--mist); stroke-width: 1; }
.chart-area { fill: var(--blush); opacity: .7; }
.chart-queue, .chart-mailboxes { fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.chart-queue { stroke: var(--ink); }
.chart-mailboxes { stroke: var(--success); }
.chart-queue-dot { fill: var(--ink); }
.chart-mailbox-dot { fill: var(--success); }
.chart-times { display: flex; height: 22px; justify-content: space-between; margin-left: 34px; color: var(--soft-muted); }
.chart-footer {
  display: flex;
  height: 44px;
  align-items: flex-end;
  justify-content: space-between;
  border-top: 1px solid var(--mist);
  color: var(--muted);
}

.protocol-card { height: 628px; padding: 30px 32px; }
.protocol-header { justify-content: space-between; }
.protocol-intro { display: flex; height: 126px; flex-direction: column; gap: 6px; padding: 28px 0 24px; }
.protocol-intro p { margin: 0; color: var(--muted); }
.protocol-row {
  display: grid;
  min-height: 76px;
  grid-template-columns: 68px 10px minmax(0, 1fr);
  gap: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--mist);
}
.protocol-row time { color: var(--soft-muted); }
.protocol-row > .status-dot { margin-top: 7px; }
.protocol-row > div { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.protocol-title, .protocol-detail { overflow-wrap: anywhere; }
.protocol-detail { color: var(--muted); }

.privacy-note { display: none; }

@media (max-width: 1180px) {
  .content { align-items: center; flex-direction: column; }
  .primary-column, .context-column { width: min(824px, 100%); flex-basis: auto; }
  .context-column { flex-direction: row; }
  .protocol-card { width: 100%; height: auto; min-height: 470px; }
}

@media (max-width: 720px) {
  body { font-size: 17px; line-height: 24px; }
  .desktop-only { display: none; }
  .mobile-only { display: initial; }
  .topbar { height: 78px; padding: 0 24px; }
  .brand { flex: 0 0 auto; gap: 10px; }
  .brand-mark { margin-right: 0; }
  .brand-subtitle { display: none; }
  .account { min-width: 0; max-width: 171px; align-items: flex-end; flex-direction: column; gap: 2px; }
  .account-email {
    order: 1;
    max-width: 100%;
    overflow: hidden;
    color: var(--ink);
    line-height: 22px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .account-status { order: 2; gap: 7px; color: var(--muted); line-height: 22px; white-space: nowrap; }
  .content { display: flex; align-items: stretch; gap: 20px; padding: 28px 24px 36px; }
  .primary-column, .context-column { display: contents; }
  .hero { order: 1; min-height: 170px; gap: 12px; padding: 0 0 4px; }
  .hero > p:first-child { margin: 0; color: var(--soft-muted); line-height: 22px; }
  .hero h1, .protocol-intro h2 { font-size: 32px; line-height: 36px; }
  .hero h1 { height: 72px; }
  .hero-title-line { display: block; width: max-content; white-space: nowrap; }
  .hero-title-line + .hero-title-line::before { content: none; }
  .hero-copy { width: auto; }
  .metrics { order: 2; height: 206px; flex: 0 0 206px; flex-direction: column; padding: 4px 20px; }
  .metric { width: 100%; height: 66px; flex: 0 0 66px; gap: 12px; padding: 0; }
  .metric + .metric { padding: 0; border-top: 1px solid #18201c24; border-left: 0; }
  .metric-value { width: 42px; flex: 0 0 42px; font-size: 32px; line-height: 36px; }
  .metric-label { font-size: 18px; line-height: 26px; }
  .metric-detail { display: flex; flex: 1; justify-content: flex-end; text-align: right; white-space: nowrap; }
  .protocol-card { order: 3; width: 100%; min-height: 469px; padding: 22px 20px; }
  .live { gap: 7px; color: var(--muted); }
  .protocol-intro { height: 128px; gap: 6px; padding: 20px 0 18px; }
  .protocol-intro p { line-height: 24px; }
  .protocol-row { min-height: 88px; grid-template-columns: 66px 10px minmax(0, 1fr); gap: 10px; padding-top: 16px; }
  .protocol-row > .status-dot { margin-top: 8px; }
  .protocol-title { font-size: 18px; line-height: 26px; }
  .chart-card { order: 4; width: 100%; height: 300px; flex: 0 0 300px; padding: 20px; }
  .chart-header { height: 58px; align-items: flex-start; flex-direction: column; gap: 10px; }
  .chart-header > div:first-child { gap: 8px; }
  .chart-legend { gap: 16px; }
  .chart-body { height: 126px; gap: 8px; padding-top: 14px; }
  .chart-axis { width: 18px; height: 112px; flex-basis: 18px; }
  .chart-mid { display: none; }
  .chart { width: calc(100% - 26px); height: 112px; }
  .chart-times { height: 22px; margin-left: 26px; }
  .chart-footer { height: 35px; align-items: flex-end; }
  .privacy-note {
    display: flex;
    order: 5;
    min-height: 196px;
    flex-direction: column;
    justify-content: space-between;
    padding: 22px 20px;
    border-radius: var(--radius);
    background: var(--ink);
    color: var(--paper);
  }
  .privacy-heading { align-items: flex-start; justify-content: space-between; font-size: 18px; line-height: 26px; }
  .privacy-heading span { max-width: 268px; }
  .privacy-heading svg { width: 22px; height: 22px; flex: 0 0 22px; fill: none; stroke: var(--paper); stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
  .privacy-note p { margin: 0; color: #e5e8e6; font-size: 18px; line-height: 26px; }
  .privacy-note .privacy-footnote { padding-top: 12px; border-top: 1px solid #3a423e; color: #b8c0bb; font-size: 17px; line-height: 22px; }
}

@media (max-width: 359px) {
  .topbar { padding-right: 12px; padding-left: 12px; }
  .content { padding-right: 8px; padding-left: 8px; }
}`
