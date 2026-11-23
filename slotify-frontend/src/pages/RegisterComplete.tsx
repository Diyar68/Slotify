import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, ApiError, errorMessage } from '../api';
import { Icon, Logo, Notice } from '../components';

export default function RegisterComplete() {
  const navigate = useNavigate();
  const [token] = useState(() => new URLSearchParams(window.location.search).get('token') ?? '');
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(true);
  const [tokenError, setTokenError] = useState('');
  const [error, setError] = useState('');
  // React-State: Diese Werte bestimmen, was das Formular und die Seite anzeigen.
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [bookingSlug, setBookingSlug] = useState('');
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);

  useEffect(() => {
    // Retain the secret only in component memory; no URL query, history entry or browser storage.
    window.history.replaceState(window.history.state, '', window.location.pathname);
    let active = true;
    api<{ registrationEnabled: boolean }>('/auth/config')
      .then((config) => {
        if (!config.registrationEnabled)
          throw new Error(
            'Die Registrierung ist in der Portfolio-Demo deaktiviert. Bitte verwende den Demo-Testzugang auf der Login-Seite.',
          );
        if (!/^[A-Za-z0-9_-]{43}$/.test(token))
          throw new Error(
            'Dieser Registrierungslink ist ungültig. Bitte fordere einen neuen Link an.',
          );
        return api<{ email: string }>(
          `/auth/registration-token/validate?token=${encodeURIComponent(token)}`,
        );
      })
      .then((result) => {
        if (active) setEmail(result.email);
      })
      .catch((error) => {
        if (active) setTokenError(errorMessage(error));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [token]);

  // Formular absenden und die Antwort des Backends verarbeiten.
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (sending.current) return;
    setError('');
    if (new TextEncoder().encode(password).length > 72) {
      setError('Das Passwort darf höchstens 72 UTF-8-Bytes lang sein.');
      return;
    }
    sending.current = true;
    setBusy(true);
    try {
      await api('/auth/register', {
        method: 'POST',
        csrf: false,
        body: { token, name: name.trim(), password, bookingSlug: bookingSlug.trim() || null },
      });
      setPassword('');
      navigate('/login', { replace: true, state: { registered: true } });
    } catch (error) {
      if (
        error instanceof ApiError &&
        error.status === 400 &&
        error.message.includes('Registrierungslink')
      ) {
        setPassword('');
        setTokenError(error.message);
      } else setError(errorMessage(error));
      sending.current = false;
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-layout">
      <section className="auth-story">
        <Logo />
        <div className="auth-story-copy">
          <span className="eyebrow">DEIN GUTER PLAN BEGINNT HIER</span>
          <h1>
            Dein Handwerk.
            <br />
            Deine Zeit.
            <br />
            <em>Gut geplant.</em>
          </h1>
          <p>
            Mit deinem Registrierungslink kannst du dein Konto vervollständigen und dich
            anschließend wie gewohnt anmelden.
          </p>
        </div>
        <div className="story-bottom">
          <span className="status-dot" />
          Ein klarer Platz für dein Business.
        </div>
      </section>
      <section className="auth-form-section">
        <div className="auth-switch">
          Schon dabei?{' '}
          <Link to="/login">
            Anmelden{' '}
            <Icon
              name="arrow"
              size={15}
            />
          </Link>
        </div>
        <div className="auth-form-wrap">
          <span className="eyebrow">REGISTRIERUNG ABSCHLIESSEN</span>
          <h2>Mach dein Konto komplett.</h2>
          {loading ? (
            <p role="status">Dein Registrierungslink wird geprüft …</p>
          ) : tokenError ? (
            <>
              <Notice>{tokenError}</Notice>
              <Link
                className="button primary full"
                to="/register"
              >
                Neuen Registrierungslink anfordern
              </Link>
            </>
          ) : (
            <>
              <p>Deine E-Mail-Adresse wird sicher aus dem Registrierungslink übernommen.</p>
              {error && <Notice>{error}</Notice>}
              <form onSubmit={submit}>
                <fieldset disabled={busy}>
                  <label>
                    Name oder Unternehmensname
                    <input
                      autoComplete="organization"
                      required
                      maxLength={100}
                      value={name}
                      onChange={(event) => setName(event.target.value)}
                      placeholder="z. B. Max Friseursalon"
                    />
                  </label>
                  <label>
                    E-Mail-Adresse
                    <input
                      type="email"
                      value={email}
                      readOnly
                      aria-readonly="true"
                    />
                  </label>
                  <label>
                    Passwort
                    <input
                      type="password"
                      autoComplete="new-password"
                      required
                      minLength={8}
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      placeholder="Mindestens 8 Zeichen"
                    />
                  </label>
                  <p className="field-hint">
                    Mindestens 8 Zeichen, höchstens 72 UTF-8-Bytes. Umlaute und Emojis benötigen
                    mehr als ein Byte.
                  </p>
                  <label>
                    Buchungskürzel <span className="optional">optional</span>
                    <input
                      maxLength={100}
                      pattern="[a-z0-9]+(-[a-z0-9]+)*"
                      value={bookingSlug}
                      onChange={(event) => setBookingSlug(event.target.value)}
                      placeholder="z. B. max-friseur"
                    />
                  </label>
                  <p className="field-hint">
                    Kleinbuchstaben, Zahlen und einzelne Bindestriche. Leer lassen? Wir erzeugen ein
                    eindeutiges Kürzel.
                  </p>
                  <button
                    className="button primary full"
                    type="submit"
                  >
                    {busy ? 'Konto wird erstellt …' : 'Konto erstellen'}
                    {!busy && (
                      <Icon
                        name="arrow"
                        size={18}
                      />
                    )}
                  </button>
                </fieldset>
              </form>
            </>
          )}
        </div>
        <footer className="auth-footer">
          Slotify V1 <span>Einfach Zeit für das Wesentliche.</span>
        </footer>
      </section>
    </main>
  );
}
