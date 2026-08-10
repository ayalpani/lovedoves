# Partnergestützte Wiederherstellung

Wiederherstellung ist möglich, solange genau ein verbundenes Gerät mit seinem
Tresor erhalten ist.

1. Das verbliebene Gerät wählt „Neues Partnergerät wiederherstellen“ und
   bestätigt biometrisch.
2. Der Relay löscht die andere Mailbox, rotiert die Schreibcapability der
   verbliebenen Mailbox und gibt eine einmalige Ersatz-Capability aus.
3. Das neue Gerät erzeugt lokal eine neue Signal-Identität und durchläuft den
   QR-/Link- sowie Sicherheitswortvergleich.
4. Nach beidseitiger biometrischer Bestätigung sendet das verbliebene Gerät ein
   Manifest und anschließend einzelne, idempotente Recovery-Batches. Fotos
   bleiben getrennte verschlüsselte Objekte.
5. Ein `DeviceRevocationV1` bindet den alten an den neuen öffentlichen
   Identitätsschlüssel.

Der alte Relay-Briefkasten wird bereits zu Beginn widerrufen. Ein abgebrochener
Versuch kann vom verbliebenen Gerät neu gestartet werden; die nächste
Ersatz-Capability ersetzt einen eventuell halb angelegten Partnerbriefkasten.
„Verlauf erneut übertragen“ ist sicher wiederholbar, weil ursprüngliche
Event-IDs erhalten bleiben.

Sind beide Geräte verloren, gibt es weder Serverbackup noch Recovery-Code. Die
Daten sind endgültig verloren.
