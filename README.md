# Love Doves

Love Doves is an open-source, private space for exactly two people. Messages
and photos are encrypted end to end, stored in an encrypted local vault, and
held by the relay only as short-lived ciphertext.

The first release targets Android 11 and newer. It is under active development
and has not yet received an independent security audit.

## Build

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME=/Users/ayalpani/Library/Android/sdk \
./gradlew assembleDebug
```

Für die tägliche Entwicklung reichen ein echtes Android-Handy und ein Emulator
als zweites Gerät. `scripts/lovedoves-dev relay` startet den lokalen Blind-Relay,
`scripts/lovedoves-dev reverse` verbindet alle erreichbaren Geräte per
`adb reverse`. Die vollständige Anleitung steht in
[`docs/development.md`](docs/development.md).

## Inhalt des MVP

- biometrisch gebundener SQLCipher-Tresor und einzeln AES-GCM-verschlüsselte Fotos
- kontoloses QR-/App-Link-Pairing mit sechs Sicherheitswörtern
- Signal-Protokoll für Text, Fotos und Zustellbestätigungen
- kurzlebiger Go-Relay für ausschließlich undurchsichtigen Chiffretext
- Partnergeräte-Ersatz mit Widerruf und paketweiser Verlaufsübertragung

Der Relay ist kein Backup. Sind beide Geräte verloren, sind auch die Inhalte
verloren.

See `docs/architecture.md` and `docs/threat-model.md` before changing storage,
pairing, transport, or cryptographic code.

## License

GNU Affero General Public License v3.0. See `LICENSE`.
