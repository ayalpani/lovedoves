# Entwicklung mit einem Handy

Ein echtes Galaxy A54 reicht zusammen mit dem vorhandenen Android-Emulator als
zweites Paargerät. Das Handy der Partnerin wird erst für Pairing-, Recovery- und
Release-Meilensteine benötigt.

## Voraussetzungen

- Android Studio mit JDK 21 und Android SDK 35
- Go 1.26
- ADB; für WLAN-ADB muss das A54 bereits gekoppelt sein
- AVD `Medium_Phone_API_36` oder ein anderer wegwerfbarer Android-11+-Emulator

## Lokaler Zwei-Geräte-Lauf

Terminal 1:

```sh
scripts/lovedoves-dev relay
```

Das Skript erzeugt ein frisches Bootstrap-Token, zeigt es einmal an und startet
den Relay auf `127.0.0.1:8787`. Terminal 2:

```sh
scripts/lovedoves-dev reverse
scripts/lovedoves-dev check
```

`reverse` richtet Port 8787 für jedes erreichbare ADB-Gerät ein. Baue und
installiere dasselbe Debug-APK auf A54 und Emulator. Das Bootstrap-Token wird
nur auf dem ersten Gerät in das Setup-Feld eingefügt; die zweite Mailbox erhält
ihre Einmal-Capability über die verschlüsselte Einladung.

Die App enthält die nötigen Debug-Bedienelemente direkt: Link/QR importieren,
manuell synchronisieren und fehlgeschlagene Nachrichten erneut senden. Für
Offline-Tests kann der Emulator isoliert werden:

```sh
scripts/lovedoves-dev offline emulator-5554 on
scripts/lovedoves-dev offline emulator-5554 off
```

`clear-emulator` verweigert echte Geräte und löscht ausschließlich die Appdaten
eines nachweislichen Emulators. Löschende Instrumentation darf nie auf dem
persönlichen A54 laufen.

## Pflichtprüfungen

```sh
scripts/lovedoves-dev check
```

Das führt Android-Unit-Tests, Debug-Assembly, Lint, Go-Tests und `go vet` aus.
Biometrie, Kamera, Photo Picker, Hintergrundsperre, Prozessneustart und
Netzverlust bleiben zusätzlich manuelle Gerätetests.
