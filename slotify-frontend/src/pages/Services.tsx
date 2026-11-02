import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, errorMessage } from '../api';
import { EmptyState, Icon, Notice, PageTitle } from '../components';
import type { ServiceOffering } from '../types';

export default function Services() {
  const [items, setItems] = useState<ServiceOffering[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState<number | null>(null);
  const [name, setName] = useState('');
  const [duration, setDuration] = useState('30');
  const formRef = useRef<HTMLFormElement>(null);

  async function load() {
    setLoading(true); setError('');
    try { setItems(await api<ServiceOffering[]>('/services')); }
    catch (error) { setError(errorMessage(error)); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);
  function reset() { setEditing(null); setName(''); setDuration('30'); }
  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError(''); setSuccess('');
    try {
      const saved = await api<ServiceOffering>(editing === null ? '/services' : `/services/${editing}`, {
        method: editing === null ? 'POST' : 'PUT', body: { name: name.trim(), duration: Number(duration) },
      });
      setItems(previous => editing === null ? [...previous, saved] : previous.map(item => item.id === saved.id ? saved : item));
      setSuccess(editing === null ? 'Dienstleistung erstellt.' : 'Änderungen gespeichert.'); reset();
    } catch (error) { setError(errorMessage(error)); }
    finally { setBusy(false); }
  }
  async function remove(item: ServiceOffering) {
    if (!window.confirm(`„${item.name}“ wirklich löschen? Dienstleistungen mit vorhandenen Buchungen können nicht gelöscht werden.`)) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      await api(`/services/${item.id}`, { method: 'DELETE' });
      setItems(previous => previous.filter(entry => entry.id !== item.id));
      if (editing === item.id) reset(); setSuccess('Dienstleistung gelöscht.');
    } catch (error) { setError(`${errorMessage(error)} Falls bereits Buchungen zugeordnet sind, kann die Dienstleistung nicht gelöscht werden.`); }
    finally { setBusy(false); }
  }
  return <>
    <PageTitle eyebrow="DEIN ANGEBOT" title="Dienstleistungen">Was du gut kannst, bekommt hier seinen Platz.</PageTitle>
    {error && <Notice>{error}</Notice>}{success && <Notice success>{success}</Notice>}
    <div className="workspace-grid">
      <section className="panel"><div className="panel-heading"><div><h2>Deine Dienstleistungen</h2><p>{loading ? 'Wird geladen …' : `${items.length} ${items.length === 1 ? 'Dienstleistung' : 'Dienstleistungen'} im Angebot`}</p></div><span className="icon-tile"><Icon name="services"/></span></div>
        {loading ? <p className="loading" role="status">Dienstleistungen werden geladen …</p> : items.length ? <ul className="service-list">{items.map(item => <li key={item.id}>
          <span className="list-symbol"><Icon name="services"/></span><div className="list-content"><h3>{item.name}</h3><p><Icon name="clock" size={14}/>{item.duration} Minuten</p></div>
          <div className="row-actions"><button className="icon-button" disabled={busy} aria-label={`${item.name} bearbeiten`} onClick={() => { setEditing(item.id); setName(item.name); setDuration(String(item.duration)); formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }); }}><Icon name="edit" size={18}/></button><button className="icon-button danger" disabled={busy} aria-label={`${item.name} löschen`} onClick={() => void remove(item)}><Icon name="trash" size={18}/></button></div>
        </li>)}</ul> : <EmptyState title="Raum für dein Angebot">Erstelle deine erste Dienstleistung mit Name und Dauer.</EmptyState>}
        {!loading && error && <button className="button secondary retry" onClick={() => void load()}>Erneut laden</button>}
      </section>
      <section className="panel form-panel"><span className="eyebrow">{editing === null ? 'ANGEBOT ERWEITERN' : 'ANGEBOT ANPASSEN'}</span><h2>{editing === null ? 'Neue Dienstleistung' : 'Dienstleistung bearbeiten'}</h2><p className="muted">Die Dauer bestimmt später die Länge eines Buchungstermins.</p>
        <form ref={formRef} onSubmit={save}><fieldset disabled={busy || loading}>
          <label>Name<input required maxLength={100} value={name} onChange={e => setName(e.target.value)} placeholder="z. B. Haarschnitt"/></label>
          <label>Dauer in Minuten<input type="number" required min={1} max={2147483647} step={1} value={duration} onChange={e => setDuration(e.target.value)}/></label>
          <button className="button primary full">{busy ? 'Wird gespeichert …' : editing === null ? 'Dienstleistung erstellen' : 'Änderungen speichern'}<Icon name={editing === null ? 'plus' : 'check'} size={18}/></button>
          {editing !== null && <button className="button secondary full" type="button" onClick={reset}>Bearbeiten abbrechen</button>}
        </fieldset></form>
      </section>
    </div>
  </>;
}
