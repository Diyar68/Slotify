import { useEffect, useState } from 'react';
import { Link, Navigate, NavLink, Outlet, Route, Routes, useNavigate } from 'react-router-dom';
import { api, ApiError, errorMessage } from './api';
import { Icon, Logo, Notice, PageTitle } from './components';
import type { Provider } from './types';
import Auth from './pages/Auth';
import Services from './pages/Services';
import Availabilities from './pages/Availabilities';
import Appointments from './pages/Appointments';

function Overview({ provider }: { provider: Provider }) {
  const areas = [
    { to: 'services', icon: 'services', number: '01', title: 'Dein Angebot', text: 'Dienstleistungen und ihre Dauer anlegen oder anpassen.', action: 'Dienstleistungen verwalten' },
    { to: 'availabilities', icon: 'calendar', number: '02', title: 'Deine Zeit', text: 'Festlegen, wann du für deine Kunden verfügbar bist.', action: 'Verfügbarkeiten planen' },
    { to: 'appointments', icon: 'bookings', number: '03', title: 'Deine Buchungen', text: 'Termine im Blick behalten und bei Bedarf stornieren.', action: 'Buchungen ansehen' },
  ];
  return <>
    <PageTitle eyebrow="DEIN PERSÖNLICHER ARBEITSPLATZ" title={`Hallo, ${provider.name}.`}>Gut, dich zu sehen. Hier bringst du deinen Tag in die richtige Reihenfolge.</PageTitle>
    <section className="welcome-panel"><div className="welcome-copy"><span className="welcome-label"><span className="status-dot"/> DEIN TAG. DEIN RHYTHMUS.</span><h2>Mehr Zeit für das,<br/>was du gerne machst.</h2><p>Dein Angebot, deine freien Zeiten, deine Kunden.<br className="desktop-break"/> Alles bereit für einen gut geplanten Tag.</p><Link className="button lime" to="availabilities">Verfügbarkeiten planen <Icon name="arrow" size={18}/></Link></div><div className="planning-illustration" aria-hidden="true"><div className="illustration-rings"/><div className="paper"><div className="paper-top"><span/><span/><span/></div><div className="paper-line short"/><div className="paper-line"/><div className="paper-slot"><span/><i/><b>✓</b></div><div className="paper-slot"><span/><i/><b>✓</b></div><div className="paper-slot muted-slot"><span/><i/><b>✓</b></div></div><span className="floating-check"><Icon name="check" size={30}/></span></div></section>
    <div className="section-heading"><h2>Alles an seinem Platz</h2><span>Deine Verwaltung</span></div>
    <div className="area-grid">{areas.map(area => <Link className="area-card" key={area.to} to={area.to}><div className="area-card-top"><span className="icon-tile"><Icon name={area.icon} size={24}/></span><span>{area.number}</span></div><h3>{area.title}</h3><p>{area.text}</p><span className="area-link">{area.action}<Icon name="arrow" size={18}/></span></Link>)}</div>
    <section className="account-strip"><span className="icon-tile"><Icon name="leaf"/></span><div><h3>Dein Buchungskürzel</h3><p>Darüber ordnet Slotify deine öffentliche Buchungsseite zu.</p></div><code>{provider.bookingSlug}</code></section>
  </>;
}

function Dashboard({ provider, onLogout }: { provider: Provider; onLogout: () => void }) {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  async function logout() {
    setBusy(true); setError('');
    try { await api('/auth/logout', { method: 'POST' }); onLogout(); navigate('/login', { replace: true }); }
    catch (error) {
      if (error instanceof ApiError && error.status === 401) { onLogout(); navigate('/login', { replace: true }); }
      else setError(errorMessage(error));
    } finally { setBusy(false); }
  }
  return <div className="app-shell">
    <aside className="sidebar"><Link className="brand-link" to="/dashboard" aria-label="Slotify Übersicht"><Logo/></Link><span className="nav-label">DEIN WORKSPACE</span>
      <nav aria-label="Hauptnavigation">
        <NavLink to="/dashboard" end><Icon name="home"/>Übersicht</NavLink>
        <NavLink to="/dashboard/services"><Icon name="services"/>Dienstleistungen</NavLink>
        <NavLink to="/dashboard/availabilities"><Icon name="calendar"/>Verfügbarkeiten</NavLink>
        <NavLink to="/dashboard/appointments"><Icon name="bookings"/>Buchungen</NavLink>
      </nav>
      <div className="sidebar-bottom"><div className="sidebar-note"><Icon name="leaf" size={23}/><strong>Ein guter Plan.<br/>Mehr Freiraum.</strong><p>Mach Platz für das Wesentliche.</p></div><button className="logout-button" disabled={busy} onClick={() => void logout()}><Icon name="logout"/>{busy ? 'Abmelden …' : 'Abmelden'}</button><span className="version">SLOTIFY V1</span></div>
    </aside>
    <div className="main-column"><header className="topbar"><span>Dein Business. <strong>Gut organisiert.</strong></span><div className="profile"><span className="avatar">{provider.name.slice(0, 1).toUpperCase()}</span><div><strong>{provider.name}</strong><span>Provider-Konto</span></div></div></header>
      <main className="workspace" id="main-content">{error && <Notice>{error}</Notice>}<Outlet/></main>
      <footer className="workspace-footer"><span>Slotify · Einfach Zeit für das Wesentliche.</span><span>Mit Ruhe geplant.</span></footer>
    </div>
  </div>;
}

export default function App() {
  const [provider, setProvider] = useState<Provider | null>(null);
  const [checking, setChecking] = useState(true);
  const [error, setError] = useState('');
  async function checkSession() {
    setChecking(true); setError('');
    try { setProvider(await api<Provider>('/auth/me')); }
    catch (error) {
      setProvider(null);
      if (!(error instanceof ApiError && error.status === 401)) setError(errorMessage(error));
    } finally { setChecking(false); }
  }
  useEffect(() => { void checkSession(); }, []);
  if (checking) return <div className="session-screen"><Logo/><p role="status">Dein Workspace wird vorbereitet …</p></div>;
  if (error) return <div className="session-screen"><Logo/><h1>Die Verbindung fehlt noch.</h1><Notice>{error}</Notice><button className="button primary" onClick={() => void checkSession()}>Verbindung erneut prüfen</button></div>;
  return <><a className="skip-link" href="#main-content">Zum Inhalt springen</a><Routes>
    <Route path="/login" element={provider ? <Navigate to="/dashboard" replace/> : <Auth key="login" onLogin={setProvider}/>}/>
    <Route path="/register" element={provider ? <Navigate to="/dashboard" replace/> : <Auth key="register" register onLogin={setProvider}/>}/>
    <Route path="/dashboard" element={provider ? <Dashboard provider={provider} onLogout={() => setProvider(null)}/> : <Navigate to="/login" replace/>}>
      <Route index element={provider ? <Overview provider={provider}/> : null}/>
      <Route path="services" element={<Services/>}/>
      <Route path="availabilities" element={<Availabilities/>}/>
      <Route path="appointments" element={<Appointments/>}/>
    </Route>
    <Route path="/" element={<Navigate to={provider ? '/dashboard' : '/login'} replace/>}/>
    <Route path="*" element={<main className="session-screen"><Logo/><h1>Diese Seite gibt es nicht.</h1><Link className="button primary" to="/">Zur Übersicht <Icon name="arrow"/></Link></main>}/>
  </Routes></>;
}
