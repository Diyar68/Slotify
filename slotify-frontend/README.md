# Slotify Frontend V1

React + TypeScript + Vite + React Router, mit einfachem CSS und nativer Fetch-API.
Kein JWT, keine zusätzlichen State- oder Komponentenbibliotheken.

## Lokal starten

Terminal 1 – Backend (PostgreSQL muss gemäß Backend-Konfiguration laufen):

```bash
cd ~/Documents/project-slotify/slotify/slotify-backend
./mvnw spring-boot:run
```

Terminal 2 – Frontend:

```bash
cd ~/Documents/project-slotify/slotify/slotify-frontend
npm ci
npm run dev
```

Im Browser **http://localhost:5173** öffnen. Port 5173 ist fest; Vite weicht nicht
unbemerkt auf einen anderen Port aus. Durchgehend localhost verwenden.

```bash
npm run build
```

Der Build prüft TypeScript und erzeugt `dist/`. `npm run preview` zeigt den Build,
konfiguriert aber keinen Entwicklungs-API-Proxy. Für die vollständige lokale
Anwendung `npm run dev` verwenden. Für ein späteres Deployment müssen Webserver
`/api` an Spring Boot weiterleiten und SPA-Routen auf `index.html` zurückführen.

## Kleine Projektstruktur

- `src/main.tsx`: React-Einstieg und BrowserRouter.
- `src/App.tsx`: Sessionprüfung, geschütztes Dashboard, Navigation und Übersicht.
- `src/api.ts`: Fetch mit Cookies, CSRF, Fehlern und Sessionablauf.
- `src/types.ts`: Typen entsprechend den tatsächlichen Backend-Responses.
- `src/components.tsx`: Wenige gemeinsame UI-Bausteine, Icons und Datumsformatierung.
- `src/pages/Auth.tsx`: Login und Anforderung eines Registrierungslinks.
- `src/pages/RegisterComplete.tsx`: Tokenprüfung und abschließende Kontoerstellung.
- `src/pages/Services.tsx`: Dienstleistungen erstellen, bearbeiten und löschen.
- `src/pages/Availabilities.tsx`: Konkrete Zeitfenster verwalten.
- `src/pages/Appointments.tsx`: Buchungen, Details und Provider-Stornierung.
- `src/styles.css`: Gemeinsames, responsives Design.
- `vite.config.ts`: React-Plugin und Entwicklungsproxy.

## Verbindung zum Backend

Alle Requests verwenden relative `/api`-URLs. Vite leitet sie in der Entwicklung
an `http://localhost:8080` weiter. Für den Browser bleibt es dieselbe Origin.
`credentials: "include"` sendet die Session-Cookies; weder Passwörter noch Tokens
oder Session-IDs werden in localStorage gespeichert.

Beim Laden prüft `/api/auth/me` die Anmeldung. Dashboard-Routen werden ohne
Provider auf `/login` umgeleitet. Der Backend-Schutz bleibt maßgeblich. Bei 401
auf Verwaltungsanfragen wird zur Anmeldung mit Hinweis auf die abgelaufene
Sitzung gewechselt. Bei einem unerreichbaren Backend gibt es eine Fehlermeldung
mit erneutem Verbindungsversuch.

Vor jedem Schreibzugriff holt der Client `/api/auth/csrf` und sendet den erhaltenen
Token im vom Backend genannten Header. Dadurch funktioniert auch die Token-
Erneuerung nach Login/Logout, ohne Cache oder zusätzliche Authentifizierung.
Nur die Registrierung verwendet die vorhandene CSRF-Ausnahme.

Verwendete APIs:

- `POST /api/auth/registration-link`, `GET /api/auth/registration-token/validate`
- `POST /api/auth/register` (Token erforderlich), `POST /api/auth/login`, `POST /api/auth/logout`
- `GET /api/auth/me`, `GET /api/auth/csrf`
- `GET/POST /api/services`, `PUT/DELETE /api/services/{id}`
- `GET/POST /api/availabilities`, `PUT/DELETE /api/availabilities/{id}`
- `GET /api/appointments`, `GET /api/appointments/{id}`,
  `PATCH /api/appointments/{id}/cancel`

Uhrzeiten werden unverändert als lokale Backend-Zeiten angezeigt. Standard ist
Europe/Berlin. Bei einer anderen Backend-Zeitzone `.env.example` nach `.env.local`
kopieren, `VITE_APP_TIME_ZONE` passend zu `SLOTIFY_TIME_ZONE` setzen und Vite neu
starten. Die ISO-Zeitfelder werden nicht versehentlich in die Browser-Zeitzone
umgerechnet.

## Überprüfung

- Dependencies installiert; `npm run build` erfolgreich.
- `npm run dev` startet auf Port 5173; SPA-Routen liefern die Anwendung aus.
- Der tatsächliche `src/api.ts`-Client wurde über Vite mit einem echten Spring-
  Boot-Backend und einer separaten PostgreSQL-Testdatenbank geprüft: 40 Requests
  für Registrierung, Fehlermeldungen, Login, Cookie-Session, CSRF, CRUD,
  Buchungsdetails, Stornierung, Logout und Sessionablauf erfolgreich.
- Die Integration verwendet echte API-Antworten, keine Mock-Daten.
- Sichtbare Formularbedienung, Browserdialoge und das Layout auf verschiedenen
  Smartphones sollten zusätzlich manuell im Browser geprüft werden.

Bekannte Backend-Grenze: Dienstleistungen mit bestehenden Buchungen können wegen
ihrer Fremdschlüssel nicht gelöscht werden. Das Frontend weist darauf hin. Die öffentliche Kundenbuchung liegt getrennt vom Provider-Dashboard unter
`/book/:bookingSlug`.

## Öffentliche Kundenbuchung

Die Route **`/book/:bookingSlug`** ist unabhängig von der Provider-Sessionprüfung.
Ein Kunde benötigt weder Konto noch Anmeldung. Die neue, minimale Backend-Abfrage
`GET /api/public/{bookingSlug}` liefert nur `name` und `services` (ID, Name, Dauer),
keine E-Mail, Passwort-Hashes oder Kundendaten.

`src/pages/Booking.tsx` führt durch Dienstleistung → Datum/Uhrzeit → Kontaktdaten
→ Bestätigung. Slots werden ausschließlich über den vorhandenen Slot-Endpunkt
berechnet. Nach einer Datums-/Dienstleistungsänderung werden alte Slots verworfen;
verspätete Antworten dürfen die neue Auswahl nicht überschreiben.

Die Buchung verwendet `POST /api/public/{bookingSlug}/appointments` mit
`serviceId`, `customerName`, `customerEmail`, `customerPhone` und `startTime`.
Diese vorhandene öffentliche POST-Route ist im Backend gezielt von CSRF ausgenommen;
die Provider-Endpunkte behalten ihren CSRF-Schutz. Die Bestätigung zeigt die vom
Backend zurückgegebenen Zeiten. Während des Sendens ist das Formular gesperrt;
eine zusätzliche Ref-Sperre verhindert unmittelbar aufeinanderfolgende Submits.
Nach Erfolg wird kein weiteres Buchungsformular angezeigt.

Bei HTTP 409 wird die Uhrzeitauswahl aufgehoben, die aktuelle Slot-Liste neu
abgerufen und eine verständliche Meldung angezeigt. Kontaktdaten bleiben erhalten.
Netzwerkfehler lösen keine automatische Wiederholung des Buchungs-POSTs aus.
Es werden weiterhin keine E-Mails verschickt.

### Lokal ausprobieren

1. Backend und Vite wie oben starten. Das Backend muss die neue öffentliche
   Provider-Abfrage enthalten; eine bereits laufende alte Version neu starten.
2. Einen Provider mit Booking-Slug `max-friseur` registrieren und anmelden.
3. Eine Dienstleistung anlegen und ein zukünftiges Verfügbarkeitsfenster erstellen.
4. In einem privaten Browserfenster **http://localhost:5173/book/max-friseur** öffnen.
5. Dienstleistung, Datum und freie Uhrzeit auswählen, Kontaktdaten eingeben und buchen.
6. Im Provider-Dashboard die neue Buchung unter „Buchungen“ prüfen.
7. Die Buchungsseite erneut öffnen: Der belegte Zeitraum darf nicht mehr angeboten
   werden. Für den Konfliktfall dieselbe Uhrzeit in zwei privaten Fenstern zuerst
   auswählen und dann nacheinander buchen: Der zweite Versuch muss einen Konflikt
   anzeigen und die verfügbaren Uhrzeiten neu laden.

Überprüft: Frontend-Build, initiales React-Routing ohne Provider-Sessiongate,
öffentliche Provider-/Dienstleistungsabfrage, Slots, anonyme Buchung ohne Cookie
und CSRF-Token, Doppelbuchungsablehnung, aktualisierte Slots und Sichtbarkeit in
der Provider-Buchungsliste. Die API-Integration lief mit dem echten Backend und
einer isolierten PostgreSQL-Datenbank über Vite. Die vollständige Backend-Suite
bestand mit 161 Tests einschließlich PostgreSQL-Parallelitätstests.
Die vollständige sichtbare Bedienung und Smartphone-Darstellung müssen noch
manuell geprüft werden; hier stand kein steuerbarer Browser dafür zur Verfügung.

## Registrierung per E-Mail-Link

Vor dem Registrieren Mailpit starten (Anleitung in der Projekt-README):

```bash
brew install mailpit
mailpit --listen 127.0.0.1:8025 --smtp 127.0.0.1:1025
```

Unter `/register` nur die E-Mail-Adresse eingeben. Die neutrale Bestätigung
verrät nicht, ob bereits ein Konto existiert. Die Test-E-Mail unter
http://localhost:8025 öffnen und ihren Link anklicken. Unter
`/register/complete?token=...` wird der Token validiert; die E-Mail ist nur lesbar.
Name, Passwort und optionales Booking-Kürzel ergänzen. Nach Erfolg folgt die
Login-Seite, kein Einrichtungsassistent.

Der Token ist 30 Minuten und nur einmal gültig. Die Abschlussseite hält ihn nur
im React-State und entfernt den Querystring aus der Adresszeile. Nach einem
Neuladen den ursprünglichen Mail-Link erneut öffnen. Ungültige/abgelaufene Links
zeigen eine Meldung mit Link zur erneuten Anforderung. Die globale Referrer-
Policy steht auf `no-referrer`; es gibt keine externen Ressourcen für diesen Ablauf.
Passwort und Token werden nie in Browser-Storage geschrieben.

Die Registrierung sendet kein frei editierbares E-Mail-Feld mehr an das Backend.
Der Server verwendet ausschließlich die E-Mail des Tokens. Der Request zum
Anfordern ist pro Adresse auf einmal pro Minute begrenzt. Bei SMTP-Problemen
bleibt die Antwort neutral; Backend-Warnung und Mailpit prüfen.

Aktuelle Überprüfung: `npm run build` erfolgreich; 170 Backend-Tests erfolgreich
inklusive PostgreSQL-Parallelität. Zusätzlich 22 echte Requests über Vite mit
echtem Mailpit-SMTP für Linkanforderung, Tokenvalidierung, Kontoerstellung,
Wiederverwendung, manipulierte E-Mail, Login, Session, CSRF und Logout erfolgreich.
Die vollständige sichtbare Browserbedienung bleibt zusätzlich manuell zu prüfen.

## Quellcode lesen und formatieren

Der Quellcode ist absichtlich ausführlich formatiert. Nur die automatisch
gebauten Dateien unter `dist/` werden kompakt/minifiziert ausgeliefert. Diese
Build-Dateien werden nicht von Hand bearbeitet.

In den React-Seiten findest du meistens dieselbe Reihenfolge:

1. **Imports**: Welche Funktionen, Komponenten und Typen die Datei verwendet.
2. **State (`useState`)**: Aktuelle Formulareingaben, Ladezustände und Fehlermeldungen.
3. **Effekte (`useEffect`)**: Zum Beispiel Daten beim Öffnen der Seite laden.
4. **Funktionen**: Ereignisse wie Speichern, Löschen und Formularabsenden behandeln.
5. **`return (...)` / JSX**: Der sichtbare Aufbau der Seite. Einrückungen zeigen,
   welche Elemente ineinander liegen.

Beispiel: Bei `value={email}` zeigt ein Eingabefeld den aktuellen State an.
`onChange={(event) => setEmail(event.target.value)}` aktualisiert diesen State,
wenn du tippst. Danach zeigt React die Oberfläche mit dem neuen Wert an.

Die gemeinsamen Formatierungsregeln stehen in `../.prettierrc.json`; die
Editor-Einrückungen in `../.editorconfig`. React/TypeScript und CSS verwenden
zwei Leerzeichen, Java vier. Prettier und sein Java-Plugin sind ausschließlich
Entwicklungswerkzeuge, keine zusätzlichen Laufzeitbibliotheken.

Im Ordner `slotify-frontend`:

```bash
npm run format          # Frontend lesbar formatieren
npm run format:backend  # Java-Quellcode und Tests formatieren
npm run format:check    # Beide Bereiche prüfen, ohne Dateien zu verändern
npm run build           # TypeScript und Produktions-Build prüfen
```

Ein Formatter entscheidet nur über die Schreibweise. Verständliche Namen,
kleine Funktionen und die Aufteilung der Verantwortlichkeiten bleiben weiterhin
wichtig. In größeren Projekten werden umfangreiche React-Komponenten häufig
zusätzlich in fachlich sinnvolle Teile aufgeteilt; dafür ist kein komplexes
Design Pattern erforderlich.
