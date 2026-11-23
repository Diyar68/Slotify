# Slotify

Slotify ist eine webbasierte Terminbuchungssoftware für Dienstleister. Anbieter
verwalten ihre Dienstleistungen, Verfügbarkeiten und eingegangenen Buchungen.
Kunden wählen über einen öffentlichen Buchungslink einen freien Termin aus und
buchen ohne eigenes Benutzerkonto.

Das Projekt wurde als Lern- und Portfolio-Projekt mit React und Spring Boot entwickelt.

## Live-Demo

| Einstieg | Link |
| --- | --- |
| **Anwendung und Anbieter-Login** | **[slotify-demo.onrender.com](https://slotify-demo.onrender.com)** |
| **Öffentliche Kundenbuchung** | **[Termin beim Demo-Anbieter buchen](https://slotify-demo.onrender.com/book/slotify-demo)** |
| Quellcode | [Diyar68/Slotify auf GitHub](https://github.com/Diyar68/Slotify) |

## Demo ausprobieren

### Als Anbieter

Öffne die Anwendung und melde dich über das Login-Formular an:

- **E-Mail:** `demo@slotify.example`
- **Passwort:** wird beim Betrieb über `SLOTIFY_DEMO_PASSWORD` festgelegt.

Ein öffentliches Demo-Passwort ist hier nicht hinterlegt. Für den Anbieterzugang
benötigst du die vom Projektbetreiber bereitgestellten Zugangsdaten. Nach der
Anmeldung stehen Dienstleistungen, Verfügbarkeiten und Buchungen im Dashboard bereit.
Die Registrierung ist im Demo-Profil deaktiviert.

### Als Kunde

Öffne die [öffentliche Buchungsseite](https://slotify-demo.onrender.com/book/slotify-demo).
Wähle eine Dienstleistung, ein Datum und eine freie Uhrzeit. Gib anschließend
fiktive Kontaktinformationen ein und bestätige die Buchung. Eine Registrierung
oder Anmeldung ist dafür nicht erforderlich.

## Funktionen

- **Anbieterkonten:** Registrierung über einen zeitlich begrenzten E-Mail-Link
  außerhalb des Demo-Modus; Anmeldung und Abmeldung mit Session-Cookie.
- **Dienstleistungen:** Angebote mit Namen und Dauer erstellen, anzeigen,
  bearbeiten und löschen.
- **Verfügbarkeiten:** Zeitfenster für konkrete Tage verwalten; ungültige und
  überschneidende Zeitfenster werden abgelehnt.
- **Freie Termine:** Startzeiten anhand von Verfügbarkeit und Dienstleistungsdauer
  berechnen; vergangene und bereits belegte Zeiten ausschließen.
- **Öffentliche Buchung:** Termine ohne Kundenkonto buchen; transaktionale
  Konfliktprüfung mit Datenbanksperre verhindert parallele Doppelbuchungen.
- **Buchungsverwaltung:** Eigene Buchungen und Kundendetails ansehen sowie
  Buchungen stornieren. Stornierte Termine werden wieder freigegeben, sofern
  Verfügbarkeit und Zeitpunkt dies erlauben.
- **Zugriffsschutz:** Anbieter können ausschließlich ihre eigenen Daten verwalten.
  Passwörter werden mit BCrypt gehasht; Session-basierte Verwaltungszugriffe
  verwenden CSRF-Schutz.

## Technologien

| Bereich | Technologien |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA, Jakarta Bean Validation |
| Frontend | React 19.3.1, TypeScript 7.0.2, Vite 8.3.5, React Router 7.18.5, CSS, Fetch API |
| Datenbank | PostgreSQL |
| Build | Maven 3.9.16 über Maven Wrapper, npm mit Lockdatei |
| Tests | JUnit 5, Mockito, Spring MockMvc; optionale PostgreSQL-Integrationstests |
| Deployment | Docker-Multi-Stage-Build, Render |
| Externes Datenbankhosting | Neon vorgesehen; Verbindung über Laufzeitkonfiguration |

Die Frontend-Versionen entsprechen dem Stand der Lockdatei. Das tatsächliche
Datenbankhosting der Live-Instanz ist aus dem Repository nicht nachweisbar.

## Architektur

```text
Browser: React-Oberfläche
          │ /api
          ▼
Spring Boot: REST-API und Spring Security
          │ Spring Data JPA
          ▼
       PostgreSQL
```

React stellt die Benutzeroberfläche bereit, Spring Boot die REST-API und
Geschäftslogik. PostgreSQL speichert Anbieter, Dienstleistungen, Verfügbarkeiten,
Buchungen und Registrierungstokens. Spring Security übernimmt die Session-basierte
Authentifizierung und den Zugriffsschutz. Es wird kein JWT verwendet.

Der Docker-Build kompiliert zunächst React und integriert die erzeugten Dateien
in das Spring-Boot-JAR. Im fertigen Image liefert Spring Boot sowohl das Frontend
als auch die API über eine gemeinsame URL aus. Die PostgreSQL-Datenbank läuft separat.
Die Runtime verwendet Java 21 und einen Benutzer ohne Root-Rechte.

```text
slotify/
├── slotify-backend/     # REST-API, Geschäftslogik, Datenzugriff und Tests
├── slotify-frontend/    # React-Oberfläche
├── Dockerfile          # Frontend-, Backend- und Runtime-Stage
└── .dockerignore
```

## Lokal starten

### Voraussetzungen

- Java 21
- Node.js 24 LTS mit npm, entsprechend der Docker-Build-Umgebung
- PostgreSQL und eine eigene lokale Entwicklungsdatenbank
- Git; Maven wird über den enthaltenen Wrapper bereitgestellt
- Für neue lokale Registrierungen: Mailpit oder ein anderer SMTP-Server

```bash
git clone https://github.com/Diyar68/Slotify.git
cd Slotify
```

### 1. PostgreSQL konfigurieren und Backend starten

Lege eine lokale Datenbank namens `slotify` an. Setze im Backend-Terminal die
Verbindungsdaten für deine eigene Entwicklungsdatenbank; die folgenden Angaben
sind Platzhalter:

```bash
cd slotify-backend
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/slotify"
export SPRING_DATASOURCE_USERNAME="<LOKALER_DATENBANKBENUTZER>"
export SPRING_DATASOURCE_PASSWORD="<LOKALES_DATENBANKPASSWORT>"
./mvnw spring-boot:run
```

Für die normale lokale Entwicklung **kein `demo`-Profil aktivieren**. Spring Boot
verwendet ohne aktives Profil die `application.properties`; Umgebungsvariablen
überschreiben deren lokale Standardwerte. Hibernate erstellt beziehungsweise
aktualisiert die Tabellen mit `ddl-auto=update`. Verwende dafür keine produktive Datenbank.

Das Backend läuft unter `http://localhost:8080`.

### 2. Frontend starten

Öffne ein zweites Terminal im Repository-Hauptverzeichnis:

```bash
cd slotify-frontend
npm ci
npm run dev
```

Öffne **[http://localhost:5173](http://localhost:5173)**. Vite leitet `/api`-Anfragen
an das Backend auf Port 8080 weiter. Cookies und CSRF werden vom vorhandenen
API-Client berücksichtigt. Verwende im Browser durchgehend `localhost`.

### 3. Lokales Anbieterkonto erstellen

Starte für die lokale E-Mail-Registrierung beispielsweise
[Mailpit](https://mailpit.axllent.org/docs/install/):

```bash
mailpit --listen 127.0.0.1:8025 --smtp 127.0.0.1:1025
```

Die Backend-Standardkonfiguration verwendet SMTP auf `localhost:1025`. Öffne
`http://localhost:5173/register`, fordere einen Registrierungslink an und rufe die
Nachricht unter `http://localhost:8025` ab. Über den Link legst du dein Konto an;
anschließend meldest du dich normal an. Bereits vorhandene lokale Konten können
ohne erneute Registrierung verwendet werden.

## Builds und Tests

```bash
# Im Verzeichnis slotify-backend
./mvnw test
./mvnw package

# Im Verzeichnis slotify-frontend
npm run build

# Im Repository-Hauptverzeichnis, bei laufendem Docker
docker build -t slotify-demo .
```

Der Frontend-Build prüft TypeScript und erzeugt `dist/`. Der Docker-Build führt
zusätzlich die Backend-Tests aus und prüft die Frontend-Dateien im JAR. Die
PostgreSQL-Parallelitätstests sind opt-in und werden ohne explizite separate
Testdatenbank übersprungen.

Für den Demo-Betrieb werden `SPRING_PROFILES_ACTIVE=demo`,
`SLOTIFY_DEMO_DB_URL`, `SLOTIFY_DEMO_DB_USERNAME`, `SLOTIFY_DEMO_DB_PASSWORD`
und `SLOTIFY_DEMO_PASSWORD` zur Laufzeit gesetzt. `PORT` bestimmt den Server-Port
(Standard 8080). Das Image aktiviert das Demo-Profil nicht automatisch. Geheimnisse
gehören ausschließlich in die lokale Umgebung oder die Hosting-Konfiguration.

## Hinweise zur Demo

- Die Anwendung dient ausschließlich Demonstrationszwecken. Verwende nur fiktive
  Namen und Kontaktdaten.
- Demo-Daten können sich durch Testbuchungen und Änderungen anderer Besucher verändern.
- Ein kostenloser Render-Web-Service kann bei Inaktivität einschlafen. Der erste
  Seitenaufruf kann deshalb länger dauern; siehe [Render Free](https://render.com/docs/free).
- Bei einem Neustart gehen bestehende Login-Sessions verloren. Die Daten bleiben
  in der externen PostgreSQL-Datenbank gespeichert.
- E-Mail-Benachrichtigungen zu Buchungen, Zahlungen, Kundenkonten und wiederkehrende
  Wochenpläne sind nicht Bestandteil von Slotify V1.
