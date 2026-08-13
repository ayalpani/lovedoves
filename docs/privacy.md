# Datenschutznotiz

Die Love-Doves-App verlangt kein Konto und verarbeitet von ihren Nutzern weder
E-Mail-Adresse, Telefonnummer, Adressbuch noch Werbe- oder Analytics-Kennung.
Der lokal gewählte Name wird ausschließlich Ende-zu-Ende-verschlüsselt
übertragen.

Getrennt davon verarbeitet der OAuth-Dienst für die nicht öffentliche
Admin-Oberfläche die E-Mail-Adresse des Betreibers. Sie dient ausschließlich
der Anmeldung und Allowlist-Prüfung, gehört zu keinem Love-Doves-Nutzerkonto
und wird nicht mit Nachrichten- oder Relay-Identifikatoren verknüpft. Auth- und
Request-Logs des OAuth-Dienstes sind deaktiviert.

Auf dem Relay fallen zufällige Capability-gebundene IDs, Chiffretextgröße, eine
auf volle Stunden reduzierte Ablaufzeit und optional eine FCM-Installations-ID
an. Der Hostinganbieter kann IP- und Verbindungsmetadaten sehen. Google kann bei
aktiviertem FCM die Installations-ID, Zeitpunkt und Netzwerkmetadaten sehen,
jedoch keinen Namen, Inhalt oder Nachrichtentyp.

Objekte und ihre SQLite-Metadaten werden nach Bestätigung sofort gelöscht; das
SQLite-Write-Ahead-Log wird anschließend trunkiert. Nicht abgeholte Objekte
werden spätestens nach 72 Stunden entfernt. Der Relay ist kein Archiv und kein
Backup.
