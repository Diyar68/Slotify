import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../api';
import { Icon, Logo, Notice } from '../components';
import type { Provider } from '../types';

export default function Auth({
  register = false,
  onLogin,
}: {
  register?: boolean;
  onLogin: (provider: Provider) => void;
}) {
  const [registrationEnabled, setRegistrationEnabled] = useState<boolean | null>(null);
  useEffect(() => {
    api<{ registrationEnabled: boolean }>('/auth/config')
      .then((config) => setRegistrationEnabled(config.registrationEnabled))
      .catch((error) => setError(errorMessage(error)));
  }, []);
  const navigate = useNavigate();
  const location = useLocation();
  // React-State: Diese Werte bestimmen, was das Formular und die Seite anzeigen.
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // Formular absenden und die Antwort des Backends verarbeiten.
  async function submit(event: FormEvent) {
    event.preventDefault();
    setError('');
    setBusy(true);
    try {
      if (register) {
        await api('/auth/registration-link', {
          method: 'POST',
          csrf: false,
          body: { email: email.trim() },
        });
        setSent(true);
      } else {
        const provider = await api<Provider>('/auth/login', {
          method: 'POST',
          body: { email: email.trim(), password },
        });
        setPassword('');
        onLogin(provider);
        navigate('/dashboard', { replace: true });
      }
    } catch (error) {
      setError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-layout">
      <section className="auth-story">
        <Logo />
        <div className="auth-story-copy">
          <span className="eyebrow">WENIGER ORGANISIEREN. MEHR MACHEN.</span>
          <h1>
            Dein Handwerk.
            <br />
            Deine Zeit.
            <br />
            <em>Gut geplant.</em>
          </h1>
          <p>
            Ein klarer Platz für deine Dienstleistungen, Verfügbarkeiten und Buchungen. Damit mehr
            Zeit für deine Kunden bleibt.
          </p>
          <div className="story-checks">
            <span>
              <Icon name="check" /> Einfach im Alltag
            </span>
            <span>
              <Icon name="check" /> Alles an einem Ort
            </span>
          </div>
        </div>
        <div className="story-bottom">
          <span className="status-dot" /> Klarer Kopf. Gut geplanter Tag.
        </div>
        <div className="story-circle circle-one" />
        <div className="story-circle circle-two" />
      </section>
      <section className="auth-form-section">
        <div className="auth-switch">
          {register ? 'Schon dabei?' : 'Neu bei Slotify?'}{' '}
          <Link to={register ? '/login' : '/register'}>
            {register ? 'Anmelden' : 'Konto erstellen'}{' '}
            <Icon
              name="arrow"
              size={15}
            />
          </Link>
        </div>
        <div className="auth-form-wrap">
          <span className="eyebrow">DEIN PROVIDER-BEREICH</span>
          <h2>{register ? 'Konto erstellen' : 'Schön, dass du da bist.'}</h2>
          <p>
            {register
              ? 'Gib deine E-Mail-Adresse ein. Wir senden dir einen Link, mit dem du dein Konto erstellen kannst.'
              : 'Melde dich an und mach deinen Tag ein Stück einfacher.'}
          </p>
          {!register && location.state?.registered && (
            <Notice success>Dein Konto ist erstellt. Du kannst dich jetzt anmelden.</Notice>
          )}
          {!register && new URLSearchParams(location.search).has('expired') && (
            <Notice>Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.</Notice>
          )}
          {error && <Notice>{error}</Notice>}
          {sent && (
            <Notice success>
              Falls die E-Mail-Adresse registriert werden kann, haben wir dir einen
              Registrierungslink gesendet.
            </Notice>
          )}
          {registrationEnabled === false && (
            <Notice>
              Dies ist eine Portfolio-Demo. Die Registrierung ist deaktiviert. Bitte verwende den
              bereitgestellten Testzugang über die Login-Seite.
            </Notice>
          )}
          {!sent && (!register || registrationEnabled === true) && (
            <form onSubmit={submit}>
              <fieldset disabled={busy}>
                <label>
                  E-Mail-Adresse
                  <input
                    type="email"
                    autoComplete="email"
                    required
                    maxLength={150}
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="du@beispiel.de"
                  />
                </label>
                {!register && (
                  <label>
                    Passwort
                    <input
                      type="password"
                      autoComplete="current-password"
                      required
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      placeholder="Dein Passwort"
                    />
                  </label>
                )}
                <button
                  className="button primary full"
                  type="submit"
                >
                  {busy ? 'Einen Moment …' : register ? 'Registrierungslink senden' : 'Anmelden'}{' '}
                  {!busy && (
                    <Icon
                      name="arrow"
                      size={18}
                    />
                  )}
                </button>
              </fieldset>
            </form>
          )}
          <p className="auth-footnote">
            <Icon
              name="check"
              size={15}
            />{' '}
            Sicher angemeldet mit einer geschützten Sitzung.
          </p>
        </div>
        <footer className="auth-footer">
          Slotify V1 <span>Einfach Zeit für das Wesentliche.</span>
        </footer>
      </section>
    </main>
  );
}
