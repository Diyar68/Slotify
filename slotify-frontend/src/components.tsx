import type { ReactNode } from 'react';

export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  const paths: Record<string, ReactNode> = {
    home: <><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1Z"/><path d="M9 21v-8h6v8"/></>,
    services: <><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M8 5V3h8v2M3 11h18M10 11v3h4v-3"/></>,
    calendar: <><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M7 3v4m10-4v4M3 11h18m-13 5h1m6 0h1"/></>,
    bookings: <><rect x="5" y="3" width="14" height="18" rx="3"/><path d="m8 14 2 2 5-5M9 7h6"/></>,
    arrow: <path d="M5 12h14m-5-5 5 5-5 5"/>,
    plus: <path d="M12 5v14M5 12h14"/>,
    logout: <><path d="M9 4H5v16h4m5-12 4 4-4 4m-5-4h10"/></>,
    clock: <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>,
    edit: <><path d="m15 4 5 5-11 11-6 1 1-6Z"/><path d="m12 7 5 5"/></>,
    trash: <><path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7"/></>,
    check: <path d="m5 12 4 4L19 6"/>,
    leaf: <><path d="M20 3C8 2 2 9 6 16s14 4 14-13Z"/><path d="m4 21 11-12"/></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] ?? paths.calendar}</svg>;
}

export function Logo() {
  return <span className="brand"><img src="/favicon.svg" alt="" width="36" height="36"/>slotify<span className="brand-dot">.</span></span>;
}
export function Notice({ children, success = false }: { children: ReactNode; success?: boolean }) {
  return <div className={`notice ${success ? 'success' : 'error'}`} role={success ? 'status' : 'alert'}>{children}</div>;
}
export function EmptyState({ title, children }: { title: string; children: ReactNode }) {
  return <div className="empty-state"><span className="empty-icon"><Icon name="leaf" size={28}/></span><h3>{title}</h3><p>{children}</p></div>;
}
export function PageTitle({ eyebrow, title, children }: { eyebrow: string; title: string; children: ReactNode }) {
  return <header className="page-title"><span className="eyebrow">{eyebrow}</span><h1>{title}</h1><p>{children}</p></header>;
}
export const timeZone = import.meta.env.VITE_APP_TIME_ZONE || 'Europe/Berlin';
export function formatDate(date: string) {
  return new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date(`${date.slice(0, 10)}T12:00:00`));
}
export function formatTime(time: string) { return time.includes('T') ? time.split('T')[1].slice(0, 5) : time.slice(0, 5); }
export function today() {
  const parts = new Intl.DateTimeFormat('en', { timeZone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  return ['year', 'month', 'day'].map(type => parts.find(part => part.type === type)!.value).join('-');
}
