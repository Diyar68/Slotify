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
- `src/pages/Auth.tsx`: Login und Registrierung.
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

- `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`
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
ihrer Fremdschlüssel nicht gelöscht werden. Das Frontend weist darauf hin. Die
öffentliche Kundenbuchungsoberfläche ist nicht Teil dieses Provider-Dashboards.
