# Threat Model

Stand: August 2026. Love Doves hat noch kein unabhängiges Sicherheitsaudit.

## Schutzversprechen

Love Doves schützt Inhalte gegen einen kompromittierten Relay, eine kopierte
App-Datenablage und neugierige Mitbenutzer eines gesperrten Geräts. Der Relay
erhält keine Signal-, Tresor- oder Foto-Schlüssel. Lokale Inhalte sind in
SQLCipher beziehungsweise AES-256-GCM verschlüsselt; private Signal-Schlüssel
verlassen den Tresor nicht.

Der Sicherheitswortvergleich bindet die beiden neu erzeugten Geräteidentitäten
an einen unabhängigen menschlichen Kanal. Wer nur den Einladungslink abfängt,
kann dadurch nicht unbemerkt als Partner bestätigt werden.

## Bewusste Grenzen

Kein Schutz besteht gegen:

- ein kompromittiertes Android-Betriebssystem oder Root-Zugriff während einer
  entsperrten Sitzung;
- eine Person, die den entsperrten Bildschirm sieht oder ein zweites Gerät zum
  Abfotografieren benutzt;
- Screenshots, Export oder Weitergabe durch den legitimen Empfänger;
- Verlust beider Geräte;
- vollständige Verbergung von IP-Adresse, Zeitpunkt, Paketanzahl und -größe vor
  Relay, Hostinganbieter, Google/FCM oder Netzbetreiber;
- Denial of Service, Löschen oder Zurückhalten noch nicht empfangener Pakete.

Bereits empfangene Fotos und Nachrichten können vom Partnergerät technisch
nicht zurückgerufen werden.

## Server- und Metadatenschutz

Capabilities haben 256 zufällige Bits und werden serverseitig nur gehasht
gespeichert. Pfade, Identifikatoren und Bodies werden nicht geloggt. Der Server
akzeptiert genau ein Paar. Ein einmaliges Bootstrap-Token öffnet die erste
Mailbox; eine daraus erzeugte Einmal-Capability öffnet die zweite.

FCM erhält nur eine Installations-ID, Zeitpunkt und Netzwerkmetadaten sowie das
inhaltslose Datenfeld `wake=1`. Namen, Partneridentität, Nachrichtentyp und
Vorschau werden nicht gesendet. Ohne FCM synchronisiert die App beim Öffnen und
periodisch mit WorkManager.

## Löschung und Wiederherstellung

„Verbindung und lokale Daten löschen“ widerruft zuerst die eigene Relay-
Mailbox. Schlägt dieser Netzwerkschritt fehl, wird nicht still lokal gelöscht.
Danach werden Transportdaten, Medien, SQLCipher-Dateien und Keystore-Aliase
entfernt. Das Partnergerät behält bereits empfangene Daten.

Bei Partner-Wiederherstellung widerruft das verbliebene Gerät die alte Mailbox,
rotiert die Schreibcapability und bestätigt die neue Signal-Identität über neue
Sicherheitswörter. Der Verlauf wird danach erneut unter der neuen Signal-
Sitzung übertragen.
