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
