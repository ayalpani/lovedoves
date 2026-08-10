# Produktpositionierung

Love Doves ist ein privater Raum für genau zwei Menschen: ohne Konto, ohne
Adressbuch und mit einem lokal biometrisch geschützten, verschlüsselten Verlauf.
Der Relay vermittelt ausschließlich kurzlebigen Chiffretext. Das
Transportprotokoll und der Client sind öffentlich überprüfbar.

## Wettbewerbsbild

- [Just Between Us](https://play.google.com/store/apps/details?id=com.getyourmarriageon.chat)
  ist der direkteste etablierte Wettbewerber mit Paar-Chat, App-Sperre und
  eigenen Verschlüsselungs- und Audit-Aussagen.
- [Between](https://play.google.com/store/apps/details?id=kr.co.vcnc.android.couple)
  bietet einen breiteren, reiferen Funktionsumfang, hält synchronisierte Daten
  laut [eigener Hilfe](https://help.between.us/hc/en-us/articles/360016320214-Will-my-data-be-deleted-if-I-log-out-or-remove-the-app)
  jedoch serverseitig vor.
- [Couples](https://www.thecouples.app/) beschreibt eine sehr ähnliche
  Privacy-Positionierung. Love Doves unterscheidet sich vor allem durch den
  offenen Quellcode, das kontolose Protokoll und den überprüfbar blinden Relay.

Die Differenzierung ist nicht „noch ein Paar-Organizer“, sondern ein enges,
nachvollziehbares Sicherheitsversprechen: lokaler Tresor, Ende-zu-Ende-
Verschlüsselung und serverseitig keine lesbaren Inhalte.

## MVP-Grenze

Der erste Release enthält ausschließlich Kopplung, Text, ein Foto oder kurzes
Video pro Nachricht, Zustellstatus, erneutes Senden, Sicherheitseinstellungen,
Löschung und partnergestützte Wiederherstellung. Reaktionen, Gruppen, GIFs,
Sprache, Ausflüge, Kalender und gemeinsame Planung gehören nicht in das MVP.

WebRTC bleibt für spätere Anrufe oder sehr große Direktübertragungen reserviert.
Asynchrone Nachrichten laufen über den blinden Relay, weil ein reines
Peer-to-Peer-Modell im Android-Hintergrund keine verlässliche Zustellung bietet
und WebRTC in vielen Netzen ebenfalls einen TURN-Relay benötigt.
