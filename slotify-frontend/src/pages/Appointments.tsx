import { useEffect, useState } from 'react';
import { api, errorMessage } from '../api';
import { EmptyState, formatDate, formatTime, Icon, Notice, PageTitle, timeZone } from '../components';
import type { Appointment } from '../types';

export default function Appointments() {
  const [items, setItems] = useState<Appointment[]>([]);
  const [selected, setSelected] = useState<Appointment | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailsLoading, setDetailsLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  async function load() {
    setLoading(true); setError('');
    try { setItems(await api<Appointment[]>('/appointments')); }
    catch (error) { setError(errorMessage(error)); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);
  async function details(item: Appointment) {
    setDetailsLoading(true); setError(''); setSuccess('');
    try { setSelected(await api<Appointment>(`/appointments/${item.id}`)); }
    catch (error) { setError(errorMessage(error)); }
    finally { setDetailsLoading(false); }
  }
  async function cancel(item: Appointment) {
    if (!window.confirm(`Buchung von ${item.customerName} am ${formatDate(item.startTime)} um ${formatTime(item.startTime)} wirklich stornieren?`)) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      const updated = await api<Appointment>(`/appointments/${item.id}/cancel`, { method: 'PATCH' });
      setItems(previous => previous.map(entry => entry.id === updated.id ? updated : entry));
      if (selected?.id === updated.id) setSelected(updated);
      setSuccess('Buchung storniert. Der Zeitraum wird wieder frei, sofern er weiterhin buchbar ist.');
    } catch (error) { setError(errorMessage(error)); }
    finally { setBusy(false); }
  }
  return <>
    <PageTitle eyebrow="DEINE KUNDENTERMINE" title="Buchungen">Alle eingegangenen Termine. Übersichtlich und an einem Ort.</PageTitle>
    {error && <Notice>{error}</Notice>}{success && <Notice success>{success}</Notice>}
    <section className="panel"><div className="panel-heading"><div><h2>Deine Buchungen</h2><p>Nach Startzeit sortiert · {timeZone}</p></div><span className="icon-tile"><Icon name="bookings"/></span></div>
      {loading ? <p className="loading" role="status">Buchungen werden geladen …</p> : items.length ? <div className="table-scroll"><table className="appointments-table"><thead><tr><th>Kunde</th><th>Dienstleistung</th><th>Termin</th><th>Status</th><th>Aktionen</th></tr></thead><tbody>{items.map(item => <tr key={item.id}>
        <td><strong>{item.customerName}</strong><span className="subtle-label">Buchung #{item.id}</span></td><td>{item.serviceOffering.name}</td>
        <td className="nowrap"><strong>{formatDate(item.startTime)}</strong><span className="subtle-label">{formatTime(item.startTime)} – {formatTime(item.endTime)} Uhr</span></td>
        <td><span className={`badge ${item.status === 'BOOKED' ? 'booked' : 'cancelled'}`}><span className="status-dot"/>{item.status === 'BOOKED' ? 'Gebucht' : 'Storniert'}</span></td>
        <td><div className="row-actions"><button className="text-button" disabled={detailsLoading || busy} onClick={() => void details(item)}>Details <Icon name="arrow" size={14}/></button>{item.status === 'BOOKED' && <button className="text-button danger" disabled={busy} onClick={() => void cancel(item)}>Stornieren</button>}</div></td>
      </tr>)}</tbody></table></div> : <EmptyState title="Platz für schöne Begegnungen">Sobald Kunden einen Termin buchen, findest du hier alle Details.</EmptyState>}
      {!loading && error && <button className="button secondary retry" onClick={() => void load()}>Erneut laden</button>}
    </section>
    {detailsLoading && <p role="status" className="loading">Buchungsdetails werden geladen …</p>}
    {selected && <section className="panel details-panel" aria-label="Buchungsdetails">
      <div className="panel-heading"><div><span className="eyebrow">BUCHUNG #{selected.id}</span><h2>{selected.customerName}</h2></div><button className="button secondary small" onClick={() => setSelected(null)}>Details schließen</button></div>
      <dl className="detail-grid">
        <div><dt>Dienstleistung</dt><dd>{selected.serviceOffering.name}</dd></div>
        <div><dt>Termin</dt><dd>{formatDate(selected.startTime)}, {formatTime(selected.startTime)}–{formatTime(selected.endTime)} Uhr</dd></div>
        <div><dt>E-Mail-Adresse</dt><dd><a href={`mailto:${selected.customerEmail}`}>{selected.customerEmail}</a></dd></div>
        <div><dt>Telefonnummer</dt><dd><a href={`tel:${selected.customerPhone}`}>{selected.customerPhone}</a></dd></div>
        <div><dt>Status</dt><dd>{selected.status === 'BOOKED' ? 'Gebucht' : 'Storniert'}</dd></div>
        <div><dt>Zeitzone</dt><dd>{timeZone}</dd></div>
      </dl>
      {selected.status === 'BOOKED' && <button className="button danger-outline" disabled={busy} onClick={() => void cancel(selected)}>Buchung stornieren</button>}
    </section>}
  </>;
}
