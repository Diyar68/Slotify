import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../api';
import { Icon, Logo, Notice } from '../components';
import type { Provider } from '../types';

export default function Auth({ register = false, onLogin }: { register?: boolean; onLogin: (provider: Provider) => void }) {
  const navigate = useNavigate();
  const location = useLocation();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [bookingSlug, setBookingSlug] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault(); setError('');
    if (register && new TextEncoder().encode(password).length > 72) {
      setError('Das Passwort darf höchstens 72 UTF-8-Bytes lang sein. Umlaute und Emojis benötigen mehr als ein Byte.'); return;
    }
    setBusy(true);
    try {
      if (register) {
        await api('/auth/register', { method: 'POST', csrf: false, body: { name, email: email.trim(), password, bookingSlug: bookingSlug.trim() || null } });
        setPassword('');
        navigate('/login', { replace: true, state: { registered: true } });
      } else {
        const provider = await api<Provider>('/auth/login', { method: 'POST', body: { email: email.trim(), password } });
        setPassword(''); onLogin(provider); navigate('/dashboard', { replace: true });
      }
    } catch (error) { setError(errorMessage(error)); }
    finally { setBusy(false); }
  }

  return <main className="auth-layout">
    <section className="auth-story">
      <Logo/>
      <div className="auth-story-copy"><span className="eyebrow">WENIGER ORGANISIEREN. MEHR MACHEN.</span>
        <h1>Dein Handwerk.<br/>Deine Zeit.<br/><em>Gut geplant.</em></h1>
        <p>Ein klarer Platz für deine Dienstleistungen, Verfügbarkeiten und Buchungen. Damit mehr Zeit für deine Kunden bleibt.</p>
        <div className="story-checks"><span><Icon name="check"/> Einfach im Alltag</span><span><Icon name="check"/> Alles an einem Ort</span></div>
      </div>
      <div className="story-bottom"><span className="status-dot"/> Klarer Kopf. Gut geplanter Tag.</div>
      <div className="story-circle circle-one"/><div className="story-circle circle-two"/>
    </section>
    <section className="auth-form-section">
      <div className="auth-switch">{register ? 'Schon dabei?' : 'Neu bei Slotify?'} <Link to={register ? '/login' : '/register'}>{register ? 'Anmelden' : 'Konto erstellen'} <Icon name="arrow" size={15}/></Link></div>
      <div className="auth-form-wrap">
        <span className="eyebrow">DEIN PROVIDER-BEREICH</span>
        <h2>{register ? 'Dein guter Plan beginnt hier.' : 'Schön, dass du da bist.'}</h2>
        <p>{register ? 'Erstelle dein Konto und bring Ruhe in deine Terminplanung.' : 'Melde dich an und mach deinen Tag ein Stück einfacher.'}</p>
        {!register && location.state?.registered && <Notice success>Dein Konto ist erstellt. Du kannst dich jetzt anmelden.</Notice>}
        {!register && new URLSearchParams(location.search).has('expired') && <Notice>Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.</Notice>}
        {error && <Notice>{error}</Notice>}
        <form onSubmit={submit}>
          <fieldset disabled={busy}>
            {register && <label>Dein Name<input autoComplete="name" required maxLength={100} value={name} onChange={e => setName(e.target.value)} placeholder="z. B. Barber Devo"/></label>}
            <label>E-Mail-Adresse<input type="email" autoComplete="email" required maxLength={150} value={email} onChange={e => setEmail(e.target.value)} placeholder="du@beispiel.de"/></label>
            <label>Passwort<input type="password" autoComplete={register ? 'new-password' : 'current-password'} required minLength={register ? 8 : undefined} value={password} onChange={e => setPassword(e.target.value)} placeholder={register ? 'Mindestens 8 Zeichen' : 'Dein Passwort'}/></label>
            {register && <>
              <p className="field-hint">Mindestens 8 Zeichen, maximal 72 UTF-8-Bytes. Dein Passwort wird nicht gekürzt.</p>
              <label>Buchungskürzel <span className="optional">optional</span><input maxLength={100} pattern="[a-z0-9]+(-[a-z0-9]+)*" value={bookingSlug} onChange={e => setBookingSlug(e.target.value)} placeholder="z. B. barber-devo"/></label>
              <p className="field-hint">Kleinbuchstaben, Zahlen und einzelne Bindestriche. Leer lassen? Wir erzeugen ein eindeutiges Kürzel für dich.</p>
            </>}
            <button className="button primary full" type="submit">{busy ? 'Einen Moment …' : register ? 'Konto erstellen' : 'Anmelden'} {!busy && <Icon name="arrow" size={18}/>}</button>
          </fieldset>
        </form>
        <p className="auth-footnote"><Icon name="check" size={15}/> Sicher angemeldet mit einer geschützten Sitzung.</p>
      </div>
      <footer className="auth-footer">Slotify V1 <span>Einfach Zeit für das Wesentliche.</span></footer>
    </section>
  </main>;
}
