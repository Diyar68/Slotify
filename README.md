# Slotify

Java 21 / Spring Boot 4.1 / PostgreSQL. Backend starten:

```bash
cd slotify-backend
./mvnw spring-boot:run
```

## Provider-Login mit Session und CSRF

Die Registrierung bleibt unter `POST /api/auth/register` erreichbar. Sie meldet
noch nicht automatisch an. Login und Logout benötigen einen CSRF-Token.
Alle folgenden Requests müssen denselben Cookie-Speicher verwenden.

### Postman

1. `GET http://localhost:8080/api/auth/csrf` aufrufen. Postman speichert das
   `JSESSIONID`-Cookie. Die Antwort enthält `headerName` und `token`.
2. `POST http://localhost:8080/api/auth/login` senden, mit
   `Content-Type: application/json` und `X-CSRF-TOKEN: <token aus Schritt 1>`:

   ```json
   {
     "email": "devo@example.com",
     "password": "MeinPasswort123!"
   }
   ```

   Das Konto muss zuvor registriert worden sein. Erfolg: HTTP 200 mit
   ProviderResponse. Die Session-ID wird erneuert; Postman muss das aktualisierte
   Cookie verwenden. Unbekannte E-Mail oder falsches Passwort: HTTP 401 mit
   derselben allgemeinen Fehlermeldung. Ungültiges JSON/ungültige Pflichtfelder:
   HTTP 400. Fehlender oder falscher CSRF-Token: HTTP 403.
3. `GET http://localhost:8080/api/auth/me` mit dem gespeicherten Session-Cookie
   liefert HTTP 200 und die Providerdaten. Ohne Anmeldung: HTTP 401.
4. Nach dem Login erneut `GET /api/auth/csrf` mit dem aktuellen Cookie aufrufen.
   Der vor dem Login erhaltene Token ist nicht mehr gültig.
5. `POST http://localhost:8080/api/auth/logout` mit dem aktuellen Cookie und dem
   neuen `X-CSRF-TOKEN` senden. Erfolg: HTTP 204. Die Session wird ungültig und
   das Session-Cookie gelöscht. `/me` liefert anschließend HTTP 401.
6. Vor einem erneuten Login wieder einen Token über `/api/auth/csrf` holen.

Für erneute Tests konsequent `localhost` verwenden, nicht zwischen `localhost`
und `127.0.0.1` wechseln. Sessions liegen im Server-Speicher und gehen bei einem
Neustart verloren.

### React

CORS erlaubt standardmäßig `http://localhost:5173` mit Cookies. Eine andere
Frontend-Adresse kann über `slotify.frontend-origin` (Umgebungsvariable
`SLOTIFY_FRONTEND_ORIGIN`) konfiguriert werden. Bei allen Fetch-Aufrufen
`credentials: "include"` verwenden; bei Login und Logout außerdem den über
`/api/auth/csrf` erhaltenen Token im angegebenen Header mitsenden. Token nach
Login und Logout erneut abrufen. Für ein Deployment HTTPS und sichere
Cookie-Einstellungen verwenden; die Vorgabe ist die lokale Entwicklung.

## Tests

```bash
cd slotify-backend
./mvnw test
```

Die MockMvc-Tests verwenden die aktive Security-Konfiguration und den echten
Service mit einem simulierten Repository. Sie verändern keine PostgreSQL-Daten.

## Dienstleistungen verwalten

Nach dem Login mit derselben Postman-Cookie-Verwaltung erneut `GET /api/auth/csrf`
aufrufen. Für POST, PUT und DELETE `X-CSRF-TOKEN: <aktueller token>` senden.
Alle Endpunkte benötigen die Provider-Anmeldung; eine `providerId` wird nicht
vom Client übernommen. Die Antwort enthält nur `id`, `name` und `duration`.

| Methode | URL | Ergebnis |
| --- | --- | --- |
| POST | `/api/services` | 201, angelegte Dienstleistung |
| GET | `/api/services` | 200, Liste der eigenen Dienstleistungen |
| PUT | `/api/services/{id}` | 200, aktualisierte Dienstleistung |
| DELETE | `/api/services/{id}` | 204, leerer Response |

Beispiel für POST:

```json
{"name": "Haarschnitt", "duration": 30}
```

Die zurückgegebene `id` für PUT und DELETE verwenden. PUT ersetzt Name und Dauer:

```json
{"name": "Haarschnitt und Bart", "duration": 45}
```

GET und DELETE benötigen keinen Request-Body. `duration` wird in Minuten
angegeben und muss vorhanden und größer als 0 sein. `name` muss vorhanden und
nicht leer sein und darf höchstens 100 Zeichen haben. Ungültige Eingaben ergeben
400, fremde oder fehlende IDs bei PUT/DELETE 404, anonyme Service-Anfragen 401.
Ein angemeldeter Schreibzugriff ohne gültigen CSRF-Token ergibt 403.

Die vorhandene JPA-Konfiguration (`ddl-auto=update`) soll beim Start die Tabelle
`service_offerings` ergänzen. Bestehende ungültige Daten werden dabei nicht
bereinigt. Die Tests verwenden simulierte Repositories und prüfen keine echte
PostgreSQL-Schemaänderung.

## Verfügbarkeiten verwalten

Nach dem Login den aktuellen CSRF-Token über `GET /api/auth/csrf` abrufen.
Session-Cookie beibehalten und bei POST, PUT und DELETE den Header
`X-CSRF-TOKEN: <token>` senden. GET benötigt nur das Cookie.

| Methode | URL | Ergebnis |
| --- | --- | --- |
| POST | `/api/availabilities` | 201, angelegtes Zeitfenster |
| GET | `/api/availabilities` | 200, eigene Zeitfenster |
| PUT | `/api/availabilities/{id}` | 200, geändertes Zeitfenster |
| DELETE | `/api/availabilities/{id}` | 204, leerer Response |

Body für POST und PUT (ein Datum in der Zukunft wählen):

```json
{
  "date": "2030-01-11",
  "startTime": "09:00",
  "endTime": "12:00"
}
```

Die Antwort enthält `id`, `date`, `startTime` und `endTime`, keine Providerdaten.
Für PUT und DELETE die zurückgegebene ID verwenden. Bei PUT alle drei Felder
angeben. GET und DELETE benötigen keinen Request-Body.

Datum und Uhrzeiten beziehen sich auf `slotify.time-zone`, standardmäßig
`Europe/Berlin`; konfigurierbar über `SLOTIFY_TIME_ZONE`. Die Prüfung vergangener
Startzeiten verwendet eine Java-Clock in dieser Zeitzone, nicht die zufällige
Zeitzone des Servers. Start heute ist erlaubt, sofern er noch nicht vergangen
ist. Ein Fenster darf nicht über Mitternacht reichen; dafür zwei Tage verwenden.

Bei einem bestehenden Fenster 09:00–12:00 am selben Tag ergibt 11:00–14:00
HTTP 409; 12:00–15:00 ist erlaubt. Verschiedene Provider dürfen identische
Fenster haben. Änderungen ignorieren das eigene bisherige Fenster beim
Überschneidungsvergleich. Fremde oder fehlende IDs ergeben 404, ungültige
Eingaben 400, anonyme Anfragen 401 und angemeldete Schreibzugriffe ohne gültigen
CSRF-Token 403.

Erstellen und Bearbeiten sperren kurz den Provider-Datensatz innerhalb der
Transaktion. Dadurch werden parallele Überschneidungsprüfungen für denselben
Provider nacheinander ausgeführt. Direkte Datenbankänderungen umgehen diese
Anwendungsprüfung. Die MockMvc-Tests nutzen eine feste Uhr und simulierte
Repositories; echte PostgreSQL-Sperren und Schemaänderungen sind nicht geprüft.

## Öffentliche Buchungszeiten

Ohne Login und ohne CSRF-Token:

```http
GET /api/public/barber-devo/slots?serviceId=1&date=2030-01-11
```

Wenn die Dienstleistung 30 Minuten dauert und für diesen Provider am gewählten
Tag 09:00–12:00 verfügbar ist, lautet die HTTP-200-Antwort:

```json
["09:00:00", "09:30:00", "10:00:00", "10:30:00", "11:00:00", "11:30:00"]
```

Die Zeiten beziehen sich wie die Verfügbarkeiten auf `slotify.time-zone`
(Standard `Europe/Berlin`, konfigurierbar über `SLOTIFY_TIME_ZONE`). Die Antwort
enthält lokale ISO-Uhrzeiten ohne UTC-Offset. Der Client muss sie zusammen mit
dem angefragten Datum und dieser Zeitzone verwenden.

Jedes Fenster bekommt ein eigenes Raster in Schritten der Dienstleistungsdauer.
Nur vollständig passende Termine werden angeboten. Vergangene Starts werden
entfernt, ohne das Raster zu verschieben; die Ergebnisse sind sortiert und ohne
Duplikate. Fenster werden auch bei direktem Anschluss nicht zusammengelegt.
Ohne passende Termine oder für vergangene Tage wird `[]` zurückgegeben.

Unbekannter Slug und fehlende/fremde Dienstleistung ergeben 404. `serviceId`
muss positiv sein, `date` ein gültiges ISO-Datum (`YYYY-MM-DD`); fehlende oder
ungültige Parameter ergeben 400. Nur diese GET-Route wurde öffentlich freigegeben.

Aktive Buchungen (`BOOKED`) werden aus den Slots herausgefiltert. `CANCELLED`
blockiert keine Zeiträume. Startzeiten müssen strikt nach der aktuellen Zeit
liegen. Die Slot-Liste selbst reserviert keinen Termin; beim Buchen wird erneut
unter einer Datenbanksperre geprüft.

## Kunde bucht einen Termin

Voraussetzung: Dienstleistung und Verfügbarkeit wurden vom Provider angelegt.
Ein zukünftiges Datum wählen und zuerst die öffentliche Slot-Liste abrufen.
Kunden benötigen weder Login noch Cookie oder CSRF-Token für diese POST-Route:

```http
POST /api/public/barber-devo/appointments
Content-Type: application/json
```

```json
{
  "serviceId": 1,
  "customerName": "Max Mustermann",
  "customerEmail": "max@example.com",
  "customerPhone": "017612345678",
  "startTime": "2030-01-11T09:30:00"
}
```

Bei einer Dienstleistung mit 30 Minuten Dauer ist die Bestätigung (HTTP 201):

```json
{
  "id": 1,
  "serviceId": 1,
  "startTime": "2030-01-11T09:30:00",
  "endTime": "2030-01-11T10:00:00",
  "status": "BOOKED"
}
```

Provider, Endzeit und Status werden ausschließlich vom Backend festgelegt.
Alle Kundendaten sind Pflichtfelder; Name maximal 100, E-Mail maximal 150 und
Telefon maximal 30 Zeichen. Die Telefonnummer wird für V1 nur auf Pflichtfeld
und Länge geprüft, nicht auf eine bestimmte nationale Schreibweise.

Ungültige Eingaben oder vergangene/aktuelle Startzeiten ergeben 400; fehlender
Provider oder fehlende/fremde Dienstleistung 404. Nicht verfügbare Starts,
einschließlich Starts außerhalb des Slot-Rasters, ergeben 409. Bei einem Fehler
wird keine Buchung gespeichert. Alle Zeiten beziehen sich auf `slotify.time-zone`.

Zum Testen einer Doppelbuchung denselben erfolgreichen Postman-Request erneut
senden: Erwartet wird 409. Anschließend darf die Slot-Liste den belegten Zeitraum
auch bei anderen Dienstleistungen desselben Providers nicht mehr anbieten.
Angrenzende Termine bleiben erlaubt, sofern sie im Raster der gewählten
Dienstleistung liegen. Es gibt noch keinen öffentlichen Stornierungsendpunkt.

Die Buchung läuft in einer Transaktion. Zuerst wird die Provider-Zeile mit
`PESSIMISTIC_WRITE` gesperrt, dann werden Dienstleistung, aktuelle Fenster und
aktive Buchungen erneut geprüft. Die Sperre bleibt bis zum Commit bestehen.
Auch Availability-Schreibzugriffe verwenden diese Provider-Sperre. Die öffentliche
POST-Route ist gezielt von CSRF ausgenommen; Provider-Endpunkte bleiben geschützt.

### PostgreSQL-Parallelitätstest

`./mvnw test` führt alle datenbankfreien Tests aus. Der echte Datenbanktest ist
ohne explizite URL deaktiviert. Ausschließlich eine leere, separate lokale
Testdatenbank verwenden: Der Test nutzt `create-drop` und entfernt ihr Schema.
Die URL muss auf `localhost` oder `127.0.0.1` mit explizitem Port zeigen; der
Datenbankname muss mit `slotify_test_` beginnen.

```bash
SLOTIFY_TEST_DB_URL=jdbc:postgresql://127.0.0.1:55439/slotify_test_appointments ./mvnw test
```

Benutzer und Passwort sind bei Bedarf über `SLOTIFY_TEST_DB_USER` und
`SLOTIFY_TEST_DB_PASSWORD` konfigurierbar (Standard: postgres / leer).
Der Test startet zwei Threads gleichzeitig gegen den echten AppointmentService
mit getrennten Transaktionen und erwartet genau eine erfolgreiche Buchung und
einen Konflikt. Er prüft außerdem, dass gespeicherte stornierte Termine nicht
blockieren und die erfolgreiche Buchung anschließend aus den Slots verschwindet.
Direkte SQL-Änderungen umgehen die Anwendungsregeln. Dienstleistungen mit
vorhandenen Buchungen können wegen der Fremdschlüssel nicht einfach gelöscht
werden; eine eigene Archivierungsregel ist noch nicht Teil von V1.

## Provider verwaltet Buchungen

Als Provider über `/api/auth/login` anmelden und das aktuelle `JSESSIONID`-Cookie
beibehalten. Nach dem Login einen frischen Token mit `GET /api/auth/csrf` holen.

| Methode | URL | Ergebnis |
| --- | --- | --- |
| GET | `/api/appointments` | 200, eigene Buchungen nach Startzeit aufsteigend |
| GET | `/api/appointments/{id}` | 200, eigene Buchungsdetails |
| PATCH | `/api/appointments/{id}/cancel` | 200, Buchung mit Status CANCELLED |

GET benötigt nur das Session-Cookie. PATCH benötigt zusätzlich den Header
`X-CSRF-TOKEN: <aktueller token>`, aber keinen Request-Body. Ohne Anmeldung ergeben
diese Routen 401. Fremde oder unbekannte IDs ergeben 404. Ein angemeldeter
PATCH-Aufruf ohne gültigen CSRF-Token ergibt 403. React verwendet wie bisher
`credentials: "include"`; PATCH ist in der bestehenden CORS-Konfiguration erlaubt.

Beispielantwort für Details bzw. Stornierung:

```json
{
  "id": 1,
  "serviceOffering": {"id": 1, "name": "Haarschnitt", "duration": 30},
  "customerName": "Max Mustermann",
  "customerEmail": "max@example.com",
  "customerPhone": "017612345678",
  "startTime": "2030-01-11T09:30:00",
  "endTime": "2030-01-11T10:00:00",
  "status": "CANCELLED"
}
```

Die Übersicht liefert eine Liste dieses Formats, einschließlich BOOKED und
CANCELLED; ohne eigene Buchungen `[]`. Kundendaten sind nur im authentifizierten
Provider-Response enthalten. Die öffentliche Buchungsbestätigung bleibt unverändert.

Die Stornierung löscht keinen Datensatz. Wiederholtes Stornieren liefert erneut
200 mit CANCELLED. Sie verwendet dieselbe Provider-Sperre wie die Buchung und
läuft in einer Transaktion. Die vorhandene Slot-Berechnung ignoriert CANCELLED
bereits; es gibt keine zweite Freigabelogik. Der Zeitraum wird wieder buchbar,
sofern er noch zukünftig ist, in einer Verfügbarkeit und im Raster liegt und
nicht durch andere aktive Buchungen blockiert wird.

Postman-Prüfung: Eine zukünftige Buchung erstellen, ihre ID aus der Übersicht
wählen und per PATCH stornieren. Danach Details erneut abrufen (Datensatz bleibt
vorhanden) und die öffentliche Slot-Liste prüfen. Anschließend denselben Zeitraum
neu buchen. Mit einem zweiten Provider müssen Detailabruf und Stornierung der
fremden ID jeweils 404 ergeben.

Der optionale PostgreSQL-Test aus dem vorherigen Abschnitt prüft jetzt zusätzlich
die echte chronologische Abfrage, gespeicherte Stornierung, Idempotenz, Erhalt der
Historie und erneute Buchbarkeit. Die Datenbank-URL muss weiterhin explizit auf
eine separate Testdatenbank zeigen; bei diesem Test wird create-drop verwendet.
