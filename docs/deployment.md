# Relay-Deployment

Produktionsziel ist `/opt/lovedoves` hinter dem vorhandenen Nginx unter
`https://lovedoves.yalpani.com`. Relay-Daten sind eine kurzlebige Queue und
werden nicht gesichert. Konfiguration und FCM-Servicekonto liegen außerhalb des
öffentlichen Repositorys.

Erforderliche Umgebungsvariablen:

```text
LOVE_DOVES_LISTEN_ADDR=:8787
LOVE_DOVES_DATA_DIR=/data
LOVE_DOVES_BOOTSTRAP_TOKEN=<nur für die erste Mailbox: 32 zufällige Bytes, base64url>
LOVE_DOVES_ADMIN_EMAIL=<exakt freigeschaltete Betreiberadresse; leer deaktiviert /admin/>
FCM_PROJECT_ID=<optional>
GOOGLE_APPLICATION_CREDENTIALS=<optionaler externer Pfad>
```

Das Token entsteht mit `go run ./cmd/lovedoves-relay bootstrap-token` im
Verzeichnis `relay/`. Es wird nie committed. Nach erfolgreicher Erzeugung der
ersten Mailbox wird `LOVE_DOVES_BOOTSTRAP_TOKEN` aus `relay.env` entfernt und
der Container neu erstellt. Die bestehende Paarung funktioniert ohne das
bereits verbrauchte Token weiter; eine leere Datenbank akzeptiert ohne Token
keine erste Mailbox.

Vor Freigabe sind TLS, `/healthz`, Requestgrößen, Dateirechte `0700`,
Container-Sandboxing, Nginx-Bodylimit und die generische FCM-Nachricht zu prüfen.

## Container und Reverse Proxy

`deploy/compose.yaml` startet den Relay und einen ausschließlich intern
erreichbaren OAuth2 Proxy im bestehenden privaten Nginx-Netz. Beide Container
sind read-only und verlieren alle Linux-Capabilities; nur der Relay schreibt in
`/opt/lovedoves/data`. Die produktive Relay-Konfiguration liegt in
`/opt/lovedoves/config/relay.env` mit Modus `0600`; der Queue-Ordner gehört der
nonroot-UID `65532` und hat Modus `0700`.

`deploy/lovedoves.nginx.conf` schaltet das Access-Log für den gesamten VHost ab,
weil URL-Pfade sonst Mailbox- und Objekt-IDs verraten würden. Der enthaltene
öffentliche Zertifikat-Fingerprint gehört zum privaten Release-Schlüssel, der
ausschließlich außerhalb des Repositorys liegt. Der VHost liefert außerdem
`assetlinks.json`, damit Android HTTPS-
Einladungen direkt Love Doves zuordnen kann.

Der Ablauf auf dem Host ist: Source nach `/opt/lovedoves/source` aktualisieren,
Container neu bauen, lokalen Healthcheck durchführen und vor dem ersten
Zertifikat kurz `deploy/lovedoves.bootstrap.nginx.conf` aktivieren. Nach DNS und
erfolgreichem ACME-Lauf ersetzt `deploy/lovedoves.nginx.conf` diesen VHost.
Beide Konfigurationen werden vor dem Neuladen geprüft. FCM bleibt aus, solange
kein externes Servicekonto konfiguriert ist;
manuelle Synchronisierung und WorkManager funktionieren unabhängig davon.

## Admin-Oberfläche und OAuth

Die read-only Admin-Oberfläche liegt unter `/admin/`. Sie zeigt ausschließlich
aggregierte Betriebsdaten: Anzahl der Mailboxen und Queue-Objekte, belegte
Bytes, aktive Rendezvous sowie den FCM- und Build-Status. Mailbox-, Objekt- und
Installations-IDs, Capabilities, Namen, Schlüssel und Inhalte werden nicht
ausgegeben.

Für Google OAuth wird ein Web-Client mit der autorisierten Redirect-URI
`https://lovedoves.yalpani.com/oauth2/callback` benötigt. Seine drei Secrets
liegen nach dem Muster `deploy/admin.env.example` in
`/opt/lovedoves/config/admin.env` mit Modus `0600`. Ein Cookie-Secret kann mit
`openssl rand -hex 16` erzeugt werden.

`/opt/lovedoves/config/admin-emails` enthält genau eine freigeschaltete
E-Mail-Adresse, gehört der nonroot-UID `65532` des OAuth-Containers und hat
Modus `0600`. Dieselbe Adresse steht als `LOVE_DOVES_ADMIN_EMAIL` in
`relay.env`. Der OAuth2 Proxy prüft die Allowlist; der Relay vergleicht die von
Nginx übernommene Adresse zusätzlich exakt. Eine leere Relay-Variable
registriert die Admin-Routen gar nicht.

Nginx schützt `/admin/` mit `auth_request` und entfernt vor der Weitergabe
Cookies sowie Authorization-Header. Bei allen öffentlichen Relay-Routen wird
der interne Admin-Header explizit geleert. Standard-, Request- und Auth-Logging
des OAuth2 Proxy sind ausgeschaltet, damit OAuth-Tokens, Betreiberadresse und
Admin-Pfade nicht in den Container-Logs erscheinen.
