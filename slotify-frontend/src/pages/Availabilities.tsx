import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, errorMessage } from '../api';
import { EmptyState, formatDate, formatTime, Icon, Notice, PageTitle, timeZone, today } from '../components';
import type { Availability } from '../types';

export default function Availabilities() {
  const [items, setItems] = useState<Availability[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [editing, setEditing] = useState<number | null>(null);
  const [date, setDate] = useState('');
  const [startTime, setStartTime] = useState('09:00');
  const [endTime, setEndTime] = useState('17:00');
  const formRef = useRef<HTMLFormElement>(null);
  async function load() {
    setLoading(true); setError('');
    try { setItems(await api<Availability[]>('/availabilities')); }
    catch (error) { setError(errorMessage(error)); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);
  function reset() { setEditing(null); setDate(''); setStartTime('09:00'); setEndTime('17:00'); }
  async function save(event: FormEvent) {
    event.preventDefault(); setError(''); setSuccess('');
    if (endTime <= startTime) { setError('Die Endzeit muss nach der Startzeit liegen.'); return; }
    setBusy(true);
    try {
      const saved = await api<Availability>(editing === null ? '/availabilities' : `/availabilities/${editing}`, {
        method: editing === null ? 'POST' : 'PUT', body: { date, startTime, endTime },
      });
      setItems(previous => editing === null ? [...previous, saved] : previous.map(item => item.id === saved.id ? saved : item));
      setSuccess(editing === null ? 'Verfügbarkeit erstellt.' : 'Änderungen gespeichert.'); reset();
    } catch (error) { setError(errorMessage(error)); }
    finally { setBusy(false); }
  }
  async function remove(item: Availability) {
    if (!window.confirm(`Verfügbarkeit am ${formatDate(item.date)} von ${formatTime(item.startTime)} bis ${formatTime(item.endTime)} löschen? Bereits gebuchte Termine bleiben bestehen.`)) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      await api(`/availabilities/${item.id}`, { method: 'DELETE' });
      setItems(previous => previous.filter(entry => entry.id !== item.id));
      if (editing === item.id) reset(); setSuccess('Verfügbarkeit gelöscht.');
    } catch (error) { setError(errorMessage(error)); }
    finally { setBusy(false); }
  }
  const sorted = [...items].sort((a, b) => `${a.date}T${a.startTime}`.localeCompare(`${b.date}T${b.startTime}`));
  return <>
    <PageTitle eyebrow="DEIN ZEITPLAN" title="Verfügbarkeiten">Du gibst den Rahmen vor. Deine Kunden finden den passenden Moment.</PageTitle>
    {error && <Notice>{error}</Notice>}{success && <Notice success>{success}</Notice>}
    <div className="workspace-grid">
      <section className="panel"><div className="panel-heading"><div><h2>Deine Zeitfenster</h2><p>Konkrete Tage · Zeitzone {timeZone}</p></div><span className="icon-tile"><Icon name="calendar"/></span></div>
        {loading ? <p className="loading" role="status">Verfügbarkeiten werden geladen …</p> : sorted.length ? <div className="table-scroll"><table><thead><tr><th>Datum</th><th>Von – bis</th><th><span className="sr-only">Aktionen</span></th></tr></thead><tbody>{sorted.map(item => <tr key={item.id}>
          <td><strong>{formatDate(item.date)}</strong>{item.date < today() && <span className="subtle-label">Vergangen</span>}</td><td className="nowrap">{formatTime(item.startTime)} – {formatTime(item.endTime)}</td>
          <td><div className="row-actions"><button disabled={busy} className="icon-button" aria-label={`Verfügbarkeit am ${formatDate(item.date)} bearbeiten`} onClick={() => { setEditing(item.id); setDate(item.date); setStartTime(item.startTime); setEndTime(item.endTime); formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }); }}><Icon name="edit" size={18}/></button><button disabled={busy} className="icon-button danger" aria-label={`Verfügbarkeit am ${formatDate(item.date)} löschen`} onClick={() => void remove(item)}><Icon name="trash" size={18}/></button></div></td>
        </tr>)}</tbody></table></div> : <EmptyState title="Deine Zeit, dein Rhythmus">Lege ein erstes Zeitfenster an, in dem Kunden buchen dürfen.</EmptyState>}
        {!loading && error && <button className="button secondary retry" onClick={() => void load()}>Erneut laden</button>}
      </section>
      <section className="panel form-panel"><span className="eyebrow">{editing === null ? 'ZEIT FREIGEBEN' : 'ZEITFENSTER ANPASSEN'}</span><h2>{editing === null ? 'Neue Verfügbarkeit' : 'Verfügbarkeit bearbeiten'}</h2><p className="muted">Ein Datum, ein Zeitfenster. Direkt angrenzende Fenster sind erlaubt.</p>
        <form ref={formRef} onSubmit={save}><fieldset disabled={busy || loading}>
          <label>Datum<input type="date" required min={today()} value={date} onChange={e => setDate(e.target.value)}/></label>
          <div className="form-row"><label>Startzeit<input type="time" required step="1" value={startTime} onChange={e => setStartTime(e.target.value)}/></label><label>Endzeit<input type="time" required step="1" value={endTime} onChange={e => setEndTime(e.target.value)}/></label></div>
          <p className="field-hint">Alle Uhrzeiten in {timeZone}. Vergangene Starts und Überschneidungen werden vom Backend abgelehnt.</p>
          <button className="button primary full">{busy ? 'Wird gespeichert …' : editing === null ? 'Zeitfenster erstellen' : 'Änderungen speichern'}<Icon name={editing === null ? 'plus' : 'check'} size={18}/></button>
          {editing !== null && <button type="button" className="button secondary full" onClick={reset}>Bearbeiten abbrechen</button>}
        </fieldset></form>
      </section>
    </div>
  </>;
}
