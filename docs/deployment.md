# Relay-Deployment

Produktionsziel ist `/opt/lovedoves` hinter dem vorhandenen Nginx unter
`https://lovedoves.yalpani.com`. Relay-Daten sind eine kurzlebige Queue und
werden nicht gesichert. Konfiguration und FCM-Servicekonto liegen außerhalb des
öffentlichen Repositorys.

Erforderliche Umgebungsvariablen:

```text
LOVE_DOVES_LISTEN_ADDR=127.0.0.1:8787
LOVE_DOVES_DATA_DIR=/opt/lovedoves/data
LOVE_DOVES_BOOTSTRAP_TOKEN=<32 zufällige Bytes, base64url>
FCM_PROJECT_ID=<optional>
GOOGLE_APPLICATION_CREDENTIALS=<optionaler externer Pfad>
```

Das Token entsteht mit `go run ./cmd/lovedoves-relay bootstrap-token` im
Verzeichnis `relay/`. Es wird nie committed. Der Release-Build enthält immer
ein leeres Bootstrap-Feld; das Token wird bei der einmaligen Einrichtung
manuell verwendet.

Vor Freigabe sind TLS, `/healthz`, Requestgrößen, Dateirechte `0700`,
Container-Sandboxing, Nginx-Bodylimit und die generische FCM-Nachricht zu prüfen.

## Container und Reverse Proxy

`deploy/compose.yaml` startet ausschließlich den Relay im bestehenden privaten
Nginx-Netz. Der Container ist read-only, verliert alle Linux-Capabilities und
schreibt nur in `/opt/lovedoves/data`. Die produktive Konfiguration liegt in
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
