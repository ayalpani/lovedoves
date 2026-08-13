# Protokoll V1

Die kanonischen Typen stehen in `protocol/lovedoves_v1.proto`. Felder werden
nur additiv weiterentwickelt; unbekannte Eventtypen dürfen eine alte App nicht
zu Klartext-Fallbacks verleiten.

## Öffentliche Typen

- `InviteV1`, `PairingReferenceV1`, `PairResponseV1` und
  `PairConfirmationV1`: kontolose Paarung und gegenseitige Bestätigung.
- `EnvelopeV1`: Signal-Nachrichtentyp und Signal-Chiffretext.
- `ConversationEventV1`: Text, Foto, Video, Sprache, Zustellbestätigung,
  Paarbestätigung, Wiederherstellung und Gerätewiderruf.
- `RecoveryManifestV1`, `RecoveryBatchV1`, `RecoveryRecordV1` und
  `DeviceRevocationV1`: idempotente Partner-Wiederherstellung.

Ein Foto besteht aus einem Signal-geschützten `PhotoMessageV1` mit Schlüssel,
Nonce und SHA-256 des verschlüsselten Blobs sowie einem getrennten Relay-
Objekt. Der Empfänger importiert das Blob und den Datenbankeintrag in einer
Transaktion; fehlt das Blob, bleibt das Envelope im lokalen Spool und wird
später erneut verarbeitet.

Ein Video verwendet `VideoMessageV1` und zwei getrennte `EncryptedMediaV1`-
Objekte: das Video und sein Vorschaubild. Dauer und Abmessungen liegen im
Signal-geschützten Ereignis. Beide Relay-Objekte besitzen unabhängige Schlüssel,
Nonces und Prüfsummen.
`round` kennzeichnet ein mit der Selfie-Kamera aufgenommenes Kreisvideo. Ältere
Clients ignorieren das additive Feld und zeigen dasselbe Medium rechteckig an.

Eine Sprachnachricht verwendet `VoiceMessageV1` mit einem `audio/mp4`-
`EncryptedMediaV1` und ihrer Dauer. Die AAC-Aufnahme wird vor Ablage und
Transport wie jedes andere Medium mit einem eigenen Schlüssel verschlüsselt.

`DeliveryReceiptV1` unterscheidet „auf dem Partnergerät gespeichert“ und
„im geöffneten Chat gesehen“. Lesebestätigungen bleiben Signal-verschlüsselt
und bündeln mehrere Nachrichten-IDs. Sie erweitern denselben Receipt-Typ
additiv, damit ältere V1-Clients sie weiterhin sicher als gewöhnliche
Zustellbestätigung behandeln. Das kurzzeitig in Vorab-Builds verwendete
`ReadReceiptV1` wird nur noch empfangen, aber nicht mehr erzeugt.

## Relay-API

Alle schreibenden oder lesenden Operationen verwenden eine Bearer-Capability.
Die zusätzlichen Header und der Partnerersatz sind administrative
Capability-Operationen, keine Benutzerkonten.

- `POST /v1/mailboxes`
- `POST /v1/mailboxes/{id}/partner-replacement`
- `PUT|GET|DELETE /v1/rendezvous/{id}`
- `PUT /v1/mailboxes/{id}/objects/{objectId}`
- `GET /v1/mailboxes/{id}/objects`
- `GET /v1/mailboxes/{id}/objects/{objectId}`
- `POST /v1/mailboxes/{id}/acks/{objectId}`
- `PUT /v1/mailboxes/{id}/push-token`
- `DELETE /v1/mailboxes/{id}`
- `GET /healthz`

Objekte sind auf 20 MiB, eine Mailbox auf 1 GiB gleichzeitigen Bestand und eine
Lebensdauer von 72 Stunden begrenzt. Rendezvous-Bodies sind höchstens 256 KiB
groß und zehn Minuten gültig. Derselbe Objektname mit identischem Chiffretext
ist idempotent; abweichender Inhalt unter derselben ID wird abgelehnt.
