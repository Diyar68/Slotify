# Slotify

Java 21 / Spring Boot 4.1 / PostgreSQL. Backend starten:

```bash
cd slotify-backend
./mvnw spring-boot:run
```

## Provider-Registrierung mit E-Mail-Link

Die Registrierung beginnt unter **http://localhost:5173/register** ausschließlich
mit der E-Mail-Adresse. Der bisherige direkte Account-Request ohne Token ist
nicht mehr gültig. Bestehende Provider melden sich unverändert an.

### Mailpit auf macOS

```bash
brew install mailpit
mailpit --listen 127.0.0.1:8025 --smtp 127.0.0.1:1025
```

Mailpit sammelt lokale Test-E-Mails; es stellt sie nicht an echte Postfächer zu.
Weboberfläche: **http://localhost:8025**. SMTP: **localhost:1025**.
Quelle: [Mailpit-Installation](https://mailpit.axllent.org/docs/install/).

Danach Backend und Frontend in separaten Terminals starten:

```bash
cd ~/Documents/project-slotify/slotify/slotify-backend
./mvnw spring-boot:run
```

```bash
cd ~/Documents/project-slotify/slotify/slotify-frontend
npm ci
npm run dev
```

1. Auf `/register` die E-Mail-Adresse eingeben und „Registrierungslink senden“ wählen.
2. Die neutrale Bestätigung erscheint auch bei bestehenden Konten oder wiederholten
   Anforderungen. Nicht anhand dieser Antwort auf einen vorhandenen Account schließen.
3. In Mailpit die E-Mail „Slotify – Registrierung abschließen“ öffnen.
4. Den Link `/register/complete?token=...` anklicken. Er gilt 30 Minuten.
5. Name, Passwort und optionales Buchungskürzel ausfüllen. Die E-Mail ist nicht
   bearbeitbar und wird serverseitig ausschließlich aus dem Token übernommen.
6. Nach Erfolg auf der Login-Seite anmelden. Kein zusätzlicher Einrichtungsassistent.

### Registrierungs-APIs

- `POST /api/auth/registration-link` mit `{"email":"max@example.com"}`:
  HTTP 200 mit derselben neutralen Meldung, ohne Token im Response.
- `GET /api/auth/registration-token/validate?token=...`: HTTP 200 mit `email`,
  andernfalls 400. Diese Abfrage meldet niemanden an.
- `POST /api/auth/register` mit gültigem Token:

```json
{
  "token": "TOKEN_AUS_DER_MAIL",
  "name": "Max Friseursalon",
  "password": "MeinPasswort123",
  "bookingSlug": "max-friseur"
}
```

Ein frei mitgeschicktes `email`-Feld wird nicht verwendet. Erfolg: 201 mit dem
bestehenden ProviderResponse. Ungültige/abgelaufene/verwendete Tokens: 400.
E-Mail- oder Slug-Konflikte: weiterhin 409. Token-Verbrauch und Provider-Erstellung
laufen in einer Transaktion; eine pessimistische Token-Zeilensperre verhindert
mehrfache Verwendung durch parallele Requests. Gespeichert werden ausschließlich
SHA-256-Hash, normalisierte E-Mail, Ablaufzeit und Verwendungszeit. Der zufällige
Token enthält 32 SecureRandom-Bytes (Base64url ohne Padding) und ist kein Login-Token.

### Konfiguration

| Umgebungsvariable | Lokaler Standard |
| --- | --- |
| `SMTP_HOST` | localhost |
| `SMTP_PORT` | 1025 |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | leer |
| `SMTP_AUTH` | false |
| `SMTP_STARTTLS` | false |
| `SMTP_FROM` | no-reply@slotify.local |
| `SLOTIFY_FRONTEND_URL` | http://localhost:5173 |

Es sind keine echten Zugangsdaten eingecheckt. Verbindungs-, Lese- und
Schreib-Timeouts betragen fünf Sekunden. Bei SMTP-Ausfall bleibt die öffentliche
Antwort ebenfalls neutral; im Backend erscheint eine allgemeine Warnung ohne
E-Mail, Link oder Token. Mailpit/SMTP prüfen und nach mindestens 60 Sekunden
erneut anfordern. Für einen echten Betrieb sind ein funktionierender SMTP-Dienst
und HTTPS notwendig; Mailpit ist ausschließlich ein lokales Testpostfach.

### Kleine Sicherheits- und V1-Grenzen

- Ein atomarer In-Memory-Cooldown erlaubt höchstens eine Link-Anforderung pro
  normalisierter E-Mail und Minute. Er gilt für eine Backend-Instanz, wird beim
  Neustart zurückgesetzt und ersetzt keinen verteilten/IP-basierten Missbrauchsschutz.
- Der Token wird im Frontend nicht gespeichert und nach dem Einlesen aus der URL
  entfernt. Bei Neuladen der Abschlussseite den ursprünglichen Mail-Link erneut
  öffnen. Nach erfolgreicher Registrierung ist er dauerhaft unbrauchbar.
- `no-referrer` verhindert die Weitergabe der URL an nachgeladene Ressourcen.
  Proxy-/Webserver-Logs dürfen Querystrings und Registrierungs-Request-Bodies
  nicht protokollieren. HTTP-Access-Logs und JDBC-Bind-Logging sind standardmäßig aus.
- Die drei Registrierungsrouten sind gezielt öffentlich. Nur die öffentlichen
  Registrierungs-POSTs sind von CSRF ausgenommen; Login und Provider-Schreibzugriffe
  behalten ihren CSRF-Schutz.
- Abgelaufene/verwendete Token-Datensätze werden für V1 nicht automatisch bereinigt.
  Mehrere angeforderte Links können bis zum Ablauf gültig bleiben; die eindeutige
  Provider-E-Mail verhindert trotzdem mehrere Konten.

### Tests

`./mvnw test` führt die datenbankfreien Tests aus. Die PostgreSQL-Tests sind nur
mit expliziter `SLOTIFY_TEST_DB_URL` für eine separate leere lokale Testdatenbank
aktiv (Name beginnt mit `slotify_test_`; `create-drop` löscht dort das Schema).

```bash
SLOTIFY_TEST_DB_URL=jdbc:postgresql://127.0.0.1:55439/slotify_test_registration ./mvnw test
```

Geprüft: Linkversand, E-Mail-Normalisierung, Hash-Speicherung, Cooldown, neutrale
Antwort, Ablauf/Verwendungsstatus, Token-Pflicht, ignorierte manipulierte E-Mail,
Passwortvalidierung und parallele Token-Verwendung. Der echte PostgreSQL-Test
bestätigt genau ein Konto und atomar gespeicherten Token-Verbrauch.

## Provider-Login mit Session und CSRF

Die Registrierung benötigt jetzt einen E-Mail-Token (siehe oben). Sie meldet
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
ungültige Parameter ergeben 400. Zusätzlich liefert `GET /api/public/{bookingSlug}`
den öffentlichen Providernamen und die angebotenen Dienstleistungen.
Verwaltungsendpunkte bleiben geschützt.

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

## Portfolio-Demo-Profil (Vorbereitung für Docker)

Ohne aktiviertes Profil bleiben die bisherigen Befehle unverändert:
`./mvnw spring-boot:run` im Backend und `npm run dev` im Frontend.
Mailpit und die lokale Registrierung funktionieren weiterhin. Es wird kein Demo-Konto angelegt.

Das Profil wird ausdrücklich über `SPRING_PROFILES_ACTIVE=demo` aktiviert.
Verwende ausschließlich eine getrennte Demo-Datenbank, niemals deine Entwicklungsdatenbank.

| Umgebungsvariable | Bedeutung |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `demo` |
| `SLOTIFY_DEMO_DB_URL` | Pflicht: PostgreSQL-JDBC-URL der Demo-Datenbank, bei Neon mit TLS |
| `SLOTIFY_DEMO_DB_USERNAME` | Pflicht: Datenbankbenutzer |
| `SLOTIFY_DEMO_DB_PASSWORD` | Pflicht: Datenbankpasswort |
| `SLOTIFY_DEMO_PASSWORD` | Pflicht: mindestens 8 Zeichen, höchstens 72 UTF-8-Bytes; kein Standardpasswort |
| `PORT` | HTTP-Port, Standard 8080 |
| `SLOTIFY_FRONTEND_ORIGIN` | Öffentliche HTTPS-Origin ohne abschließenden Slash |
| `SLOTIFY_FRONTEND_URL` | Öffentliche HTTPS-URL; Registrierung in der Demo deaktiviert |
| `SLOTIFY_TIME_ZONE` | Standard `Europe/Berlin`; muss zur Frontend-Zeitzone passen |

Die Demo-Datenbankvariablen haben bewusst keine lokalen Ersatzwerte. Spring Boot
unterstützt zusätzlich seine üblichen Property-Overrides; auch dabei niemals auf
Entwicklungsdaten zeigen. Geheimnisse nur in der Hosting-Konfiguration hinterlegen.

Das Demo-Profil deaktiviert SQL-Ausgaben, verarbeitet Forwarded Headers des
vorgeschalteten vertrauenswürdigen Reverse Proxys und setzt Session-Cookies auf
Secure, HttpOnly und SameSite=Lax. Deshalb erfolgt der Browserbetrieb über HTTPS.
Die lokale Vite-Entwicklung verwendet weiterhin das normale Profil ohne Secure-Zwang.
Der Dienst darf mit diesen Forwarded-Header-Einstellungen nicht ungeschützt an einem
nicht vertrauenswürdigen Proxy vorbei öffentlich erreichbar sein.

### Demo-Zugang und Beispieldaten

Login-E-Mail: `demo@slotify.example`. Passwort: der konfigurierte Wert von
`SLOTIFY_DEMO_PASSWORD`. Booking-Slug: `slotify-demo`.
Es gelten dieselbe BCrypt-Prüfung, Provider-Rolle, Session und CSRF-Regeln wie bei
jedem anderen Provider. Ein fehlendes oder ungültiges Passwort verhindert den Start.
Bei einer bewussten Passwortänderung in der Konfiguration wird der Hash aktualisiert.

Beim ersten Start werden der fiktive Provider, Beratung (30 Minuten), Styling
(45 Minuten) und 14 Tagesfenster ab morgen (09–17 Uhr in der Anwendungszeitzone)
angelegt. Es werden keine Kunden oder festen Beispielbuchungen angelegt.
Wiederholte Starts behalten vorhandene Daten und Änderungen. Erst wenn kein
Verfügbarkeitsfenster nach heute mehr existiert, werden wieder 14 Tage ergänzt.
Gelöschte oder bearbeitete Dienstleistungen werden nicht automatisch zurückgesetzt.
Die Initialisierung ist für die geplante einzelne Anwendungsinstanz gedacht.

`GET /api/auth/config` liefert nur `registrationEnabled`. Im Demo-Profil sind
Linkanforderung, Tokenprüfung und Registrierung gesperrt (403 bei ansonsten gültigen
Requests, ungültige Eingaben können vorher 400 ergeben). Es werden dabei weder
Tokens noch Konten erstellt oder E-Mails versendet. Die Registrierungsseiten zeigen
einen Demo-Hinweis. Das Passwort wird nicht über die Konfigurations-API veröffentlicht.

### React aus Spring Boot

Der spätere Docker-Build muss den Inhalt von `slotify-frontend/dist/` vor dem
Maven-Package-Schritt nach `slotify-backend/src/main/resources/static/` kopieren.
Dieser Schritt ist hier noch nicht automatisiert; ohne diese Dateien existiert im
Backend keine React-Oberfläche. Der Vite-Proxy bleibt lokal unverändert.

Der SPA-Controller leitet nur bekannte Frontend-Routen auf `/index.html` weiter.
Neue React-Seiten müssen dort und gegebenenfalls in Security ergänzt werden.
APIs und fehlende Assets bekommen keinen HTML-Fallback. Bekannte Provider-APIs
bleiben geschützt; unbekannte Routen werden abgewiesen (404 statt Login-Umleitung).

### Spätere Rücksetzung (noch nicht implementiert)

Kein öffentlicher Reset-Endpunkt und keine Löschung beim Start. Für eine spätere
manuelle Rücksetzung: Dienst kurz anhalten, Demo-Datenbank sichern, nur Daten des
Demo-Providers entfernen (Buchungen vor Verfügbarkeiten/Dienstleistungen und Provider),
dann mit dem Demo-Profil neu starten. Andere Provider bleiben erhalten. Alternativ
kann eine vollständig isolierte, ausschließlich für diese Demo verwendete Datenbank
bewusst neu angelegt werden. Niemals gegen lokale Entwicklungsdaten ausführen.
Ein Reset-Job, Migrationen, Missbrauchslimits und Docker gehören zu späteren Schritten.

Öffentliche Testzugangsdaten ermöglichen allen Besuchern Änderungen an denselben
Demo-Daten. Nur fiktive Kundendaten verwenden. Sessions gehen bei Neustarts verloren.

Überprüfung dieses Vorbereitungsschritts:
- `./mvnw package`: 180 Tests erfolgreich, einschließlich der drei opt-in
  PostgreSQL-Tests gegen eine getrennte temporäre Datenbank.
- `npm run build`: TypeScript-Prüfung und Vite-Build erfolgreich.
- Demo-JAR mit extern eingebundenem `dist/`: 23 HTTP-Prüfungen vor und nach
  Neustart erfolgreich (Seiten/Assets, 404, Login/Logout, CSRF, Cookies, Registrierung).
  Nach Neustart weiterhin ein Demo-Provider, zwei Dienstleistungen, 14 Fenster, keine Tokens.
- Normaler Start ohne Profil gegen eine leere separate Testdatenbank und Vite:
  fünf HTTP-/Proxy-Prüfungen erfolgreich, Registrierung aktiviert, kein Demo-Provider.
- Kein Docker-Image, kein Live-HTTPS-Proxy und keine visuelle Browserprüfung in diesem Schritt.

## Gemeinsames Docker-Image (Frontend und Backend)

Aus dem Repository-Hauptverzeichnis bauen, während Docker Desktop läuft:

```bash
docker build -t slotify-demo .
```

Drei Stages: `node:24-bookworm-slim` baut React mit `npm ci` und `npm run build`;
`eclipse-temurin:21-jdk-noble` baut mit dem vorhandenen Maven Wrapper (Maven 3.9.16)
das JAR einschließlich Tests; `eclipse-temurin:21-jre-noble` führt nur das JAR aus.
Es wird kein separates Maven-Image benötigt. Die Maven-Downloads werden per
BuildKit-Cache wiederverwendet. Im Build werden index.html, JS, CSS und Favicon
im fertigen JAR geprüft. Lokale Build-Ausgaben gelangen nicht in den Build-Kontext.
Die Basis-Tags erhalten Updates; identische Lockdateien garantieren deshalb nicht
bei jedem späteren Build ein bytegleiches Gesamtimage.

Die Runtime läuft mit UID/GID 10001, ohne Node, npm oder Maven. `PORT` wird beim
Start ausgewertet (Standard 8080); der Server bindet auf `0.0.0.0`. `EXPOSE 8080`
ist nur eine Dokumentation und veröffentlicht selbst keinen Port.
Ein Profil oder Zugangsdaten werden nicht in das Image eingebaut. Kein Secret
gehört in Dockerfile, Build-Argumente oder Frontend-Variablen (`VITE_*` ist öffentlich).

### Start mit externer Demo-Datenbank

Lege eine lokale, von Git und Docker ausgeschlossene Datei `.env.demo` an.
Die folgenden Werte sind Platzhalter und müssen ersetzt werden:

```dotenv
SPRING_PROFILES_ACTIVE=demo
SLOTIFY_DEMO_DB_URL=jdbc:postgresql://<NEON-HOST>/<DEMO-DATENBANK>?sslmode=require
SLOTIFY_DEMO_DB_USERNAME=<DEMO-DATENBANKBENUTZER>
SLOTIFY_DEMO_DB_PASSWORD=<DEMO-DATENBANKPASSWORT>
SLOTIFY_DEMO_PASSWORD=<DEMO-LOGINPASSWORT>
PORT=8080
SLOTIFY_FRONTEND_URL=https://<DEMO-HOST>
SLOTIFY_FRONTEND_ORIGIN=https://<DEMO-HOST>
```

Nutze eine eigene Datenbank für die Demo. Die JDBC-URL benötigt bei Neon TLS.
Aus einem Container bezeichnet `localhost` den Container selbst, nicht den Mac
und nicht einen anderen Datenbankcontainer. Bei einer lokalen Containerdatenbank
verwende ein eigenes Docker-Netzwerk und deren Containernamen als Datenbankhost.

Für einen lokalen Browser-Test über **HTTP**, mit `PORT=8080` in der Datei:

```bash
docker run -d --name slotify-demo --env-file .env.demo \
  -e SERVER_SERVLET_SESSION_COOKIE_SECURE=false \
  -p 127.0.0.1:8080:8080 slotify-demo
```

Öffne http://localhost:8080. Login: `demo@slotify.example` mit dem selbst gesetzten
Demo-Passwort. Öffentliche Buchungsseite: http://localhost:8080/book/slotify-demo.
Falls die Datenbank erst aufwacht, kann der Start länger dauern.

**Die Cookie-Ausnahme gilt ausschließlich für lokalen HTTP-Testbetrieb.** Auf
Render/HTTPS diese Variable weglassen; dort bleibt das Cookie Secure. Für einen
vollständigen Test der HTTPS-Proxy-Einstellungen ist ein vertrauenswürdiger lokaler
HTTPS-Proxy oder das spätere Render-Deployment erforderlich.

Logs, Neustart und Beenden:

```bash
docker logs --tail 100 slotify-demo
docker restart slotify-demo
docker stop slotify-demo
docker rm slotify-demo
```

Neustarts behalten die externe Datenbank und duplizieren die Demo-Daten nicht.
Sessions im Anwendungsspeicher gehen bei einem Neustart verloren. Ein neuer
Container ist erforderlich, um geänderte Umgebungsvariablen zu übernehmen.
Für `PORT=9090` muss auch die Portweiterleitung angepasst werden, beispielsweise
`-p 127.0.0.1:9090:9090`. Die URL lautet dann http://localhost:9090.
Ohne Demo-Profil wird kein Demo-Konto initialisiert. Die normale lokale
Backend-/Vite-Entwicklung bleibt unverändert und benötigt Docker nicht.

### Später auf Render

- Repository-Hauptverzeichnis als Build-Kontext, Dockerfile `./Dockerfile`.
- Ein Docker-Web-Service; PostgreSQL separat bei Neon.
- Profil, Demo-Datenbankvariablen und Demo-Passwort erst zur Laufzeit konfigurieren.
- Keine Secret-`ARG`-Deklarationen ergänzen: Render stellt Dienstvariablen auch
  als mögliche Build-Argumente bereit; unser Dockerfile verwendet sie nicht.
- Render stellt `PORT` bereit und übernimmt öffentliches HTTPS. Keine lokale
  Cookie-Ausnahme setzen; öffentliche URL und Origin passend konfigurieren.
- Optionaler Render-HTTP-Healthcheck: `/`. Kein zusätzlicher Docker-Healthcheck
  und keine neue öffentliche Diagnose-API sind erforderlich. `/` prüft die
  HTTP-/Frontend-Auslieferung, nicht umfassend die Datenbankgesundheit.
- Der lokale Build auf einem Apple-Silicon-Mac ist ARM64. Render soll das Image
  selbst aus dem Dockerfile für seine Architektur bauen. Ein später direkt
  hochgeladenes Image müsste zur Zielarchitektur passen.
- Kostenloser Render-Betrieb kann einschlafen; Registrierung bleibt in der Demo
  deaktiviert. Keine Render- oder Neon-Ressourcen wurden in diesem Schritt angelegt.

Docker-Überprüfung dieses Schritts:
- `docker build -t slotify-demo .` erfolgreich auf Docker Desktop / ARM64.
- Im Build: Frontend-Build erfolgreich; Maven 180 Tests, davon 177 erfolgreich
  und 3 opt-in PostgreSQL-Tests übersprungen. Diese drei Tests wurden im Docker-Build
  nicht gegen eine echte Datenbank ausgeführt.
- JAR aus dem fertigen Runtime-Container geprüft: HTML, JavaScript, CSS und SVG
  vorhanden; Runtime UID/GID 10001, Java vorhanden, Node/npm/Maven nicht vorhanden.
- 33 HTTP-Prüfungen vor und nach Container-Neustart erfolgreich: React-Routen,
  Assets, 404, Authentifizierung, CSRF, Cookie-Attribute, Registrierungssperre,
  öffentliche Buchung, Konflikt bei Doppelbuchung und Freigabe nach Stornierung.
- Separate PostgreSQL-17-Containerdatenbank, nur über eigenes Docker-Netzwerk,
  ohne veröffentlichten Datenbankport. Daten nach App-Neustart unverändert;
  ein Demo-Provider, zwei Dienstleistungen, 14 Verfügbarkeiten. Testbuchungen
  blieben erhalten. Keine lokale Entwicklungsdatenbank wurde verwendet.
- Alternativer `PORT=18081` erfolgreich; ohne Demo-Profil keine Demo-Daten,
  Registrierung aktiviert. Fehlende Demo-DB-URL, DB-Benutzer, DB-Passwort oder
  Demo-Passwort verhindern jeweils den Start. Testpasswörter nicht in den
  geprüften Container-Logs (stdout/stderr), Image-Konfiguration oder Image-History.
- HTTPS wurde per Forwarded-Header simuliert; kein echter TLS-Proxy, kein visueller
  Browser-Test, kein AMD64-Laufzeittest und kein Render-/Neon-Test in diesem Schritt.
