import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useParams } from 'react-router-dom';
import { api, ApiError, errorMessage } from '../api';
import {
  EmptyState,
  formatDate,
  formatTime,
  Icon,
  Logo,
  Notice,
  timeZone,
  today,
} from '../components';
import type { BookingConfirmation, PublicProvider } from '../types';

export default function BookingRoute() {
  const { bookingSlug = '' } = useParams();
  // Switching to a different provider must clear contact details and confirmation.
  return (
    <Booking
      key={bookingSlug}
      bookingSlug={bookingSlug}
    />
  );
}

function Booking({ bookingSlug }: { bookingSlug: string }) {
  const base = `/public/${encodeURIComponent(bookingSlug)}`;
  // React-State: Diese Werte bestimmen, was das Formular und die Seite anzeigen.
  const [provider, setProvider] = useState<PublicProvider | null>(null);
  const [loading, setLoading] = useState(true);
  const [providerError, setProviderError] = useState('');
  const [providerRetry, setProviderRetry] = useState(0);
  const [serviceId, setServiceId] = useState<number | null>(null);
  const [date, setDate] = useState(today);
  const [slots, setSlots] = useState<string[]>([]);
  const [slotsLoading, setSlotsLoading] = useState(false);
  const [slotsError, setSlotsError] = useState('');
  const [slotsRetry, setSlotsRetry] = useState(0);
  const [selectedTime, setSelectedTime] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerEmail, setCustomerEmail] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [confirmation, setConfirmation] = useState<BookingConfirmation | null>(null);
  const submissionLock = useRef(false);
  const service = provider?.services.find((item) => item.id === serviceId);
  const step = confirmation ? 4 : selectedTime ? 3 : serviceId ? 2 : 1;

  useEffect(() => {
    let active = true;
    setLoading(true);
    setProviderError('');
    api<PublicProvider>(base)
      .then((result) => {
        if (active) setProvider(result);
      })
      .catch((error) => {
        if (active)
          setProviderError(
            error instanceof ApiError && error.status === 404
              ? 'Diese Buchungsseite wurde nicht gefunden. Bitte prüfe den Link deines Anbieters.'
              : errorMessage(error),
          );
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [base, providerRetry]);

  useEffect(() => {
    let active = true;
    setSlots([]);
    setSelectedTime('');
    setSlotsError('');
    if (!serviceId || !date || confirmation) {
      setSlotsLoading(false);
      return;
    }
    setSlotsLoading(true);
    api<string[]>(`${base}/slots?serviceId=${serviceId}&date=${encodeURIComponent(date)}`)
      .then((result) => {
        if (active) setSlots(result);
      })
      .catch((error) => {
        if (active) setSlotsError(errorMessage(error));
      })
      .finally(() => {
        if (active) setSlotsLoading(false);
      });
    // Ignore outdated replies after a date/service change; slots only come from the backend.
    return () => {
      active = false;
    };
  }, [base, serviceId, date, slotsRetry, confirmation]);

  // Verbindlich buchen. Das Backend prüft den Termin nochmals.
  async function book(event: FormEvent) {
    event.preventDefault();
    if (submissionLock.current || confirmation || !service || !selectedTime || slotsLoading) return;
    submissionLock.current = true;
    setSubmitting(true);
    setSubmitError('');
    try {
      const result = await api<BookingConfirmation>(`${base}/appointments`, {
        method: 'POST',
        csrf: false,
        body: {
          serviceId: service.id,
          customerName: customerName.trim(),
          customerEmail: customerEmail.trim(),
          customerPhone: customerPhone.trim(),
          startTime: `${date}T${selectedTime}`,
        },
      });
      setConfirmation(result);
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setSubmitError(
          'Dieser Termin ist inzwischen nicht mehr verfügbar. Bitte wähle eine neue Uhrzeit. Deine Kontaktdaten bleiben erhalten.',
        );
        setSelectedTime('');
        setSlotsRetry((previous) => previous + 1);
      } else if (error instanceof ApiError && error.status === 0) {
        setSubmitError(
          'Die Buchungsbestätigung konnte nicht empfangen werden. Bitte sende die Anfrage nicht sofort erneut ab; möglicherweise wurde der Termin bereits gebucht.',
        );
      } else {
        setSubmitError(errorMessage(error));
        if (error instanceof ApiError && error.status === 400) {
          setSelectedTime('');
          setSlotsRetry((previous) => previous + 1);
        }
      }
      submissionLock.current = false;
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="booking-page">
      <header className="booking-topbar">
        <Logo />
        <span>Einfach deinen passenden Moment finden.</span>
      </header>
      <main
        className="booking-main"
        id="main-content"
      >
        <header className="booking-intro">
          <span className="eyebrow">DEIN TERMIN. GANZ UNKOMPLIZIERT.</span>
          <h1>{provider ? provider.name : 'Dein nächster Termin.'}</h1>
          <p>Wähle deine Dienstleistung und einen freien Termin. Ganz ohne Kundenkonto.</p>
        </header>
        {loading ? (
          <div
            className="panel loading"
            role="status"
          >
            Dienstleistungen werden geladen …
          </div>
        ) : providerError ? (
          <section className="panel booking-section">
            <Notice>{providerError}</Notice>
            <button
              className="button secondary"
              onClick={() => setProviderRetry((previous) => previous + 1)}
            >
              Erneut versuchen
            </button>
          </section>
        ) : (
          provider && (
            <>
              <ol
                className="booking-steps"
                aria-label="Buchungsschritte"
              >
                {['Dienstleistung', 'Termin', 'Kontaktdaten', 'Bestätigung'].map((label, index) => (
                  <li
                    key={label}
                    className={index + 1 <= step ? 'reached' : ''}
                    aria-current={index + 1 === step ? 'step' : undefined}
                  >
                    <span>
                      {index + 1 < step ? (
                        <Icon
                          name="check"
                          size={15}
                        />
                      ) : (
                        index + 1
                      )}
                    </span>
                    {label}
                  </li>
                ))}
              </ol>
              {confirmation ? (
                <section
                  className="panel booking-confirmation"
                  role="status"
                >
                  <span className="confirmation-icon">
                    <Icon
                      name="check"
                      size={34}
                    />
                  </span>
                  <span className="eyebrow">ALLES GUT GEPLANT</span>
                  <h2>Dein Termin ist gebucht.</h2>
                  <p>Wir freuen uns auf dich bei {provider.name}.</p>
                  <dl className="confirmation-details">
                    <div>
                      <dt>Dienstleistung</dt>
                      <dd>{service?.name}</dd>
                    </div>
                    <div>
                      <dt>Datum</dt>
                      <dd>{formatDate(confirmation.startTime)}</dd>
                    </div>
                    <div>
                      <dt>Uhrzeit</dt>
                      <dd>
                        {formatTime(confirmation.startTime)}–{formatTime(confirmation.endTime)} Uhr
                      </dd>
                    </div>
                    <div>
                      <dt>Buchungsnummer</dt>
                      <dd>#{confirmation.id}</dd>
                    </div>
                  </dl>
                  <p className="field-hint">
                    Alle Uhrzeiten in {timeZone}. Bitte notiere dir deinen Termin. Es wird keine
                    E-Mail-Bestätigung versendet.
                  </p>
                </section>
              ) : provider.services.length === 0 ? (
                <section className="panel">
                  <EmptyState title="Hier ist es noch ruhig.">
                    Dieser Anbieter hat derzeit keine Dienstleistungen zur Buchung eingestellt.
                  </EmptyState>
                </section>
              ) : (
                <form onSubmit={book}>
                  <fieldset
                    disabled={submitting}
                    className="booking-controls"
                  >
                    <section className="panel booking-section">
                      <div className="booking-section-heading">
                        <span className="step-number">01</span>
                        <div>
                          <h2>Was darf es sein?</h2>
                          <p>Wähle deine Dienstleistung.</p>
                        </div>
                      </div>
                      <div
                        className="booking-service-grid"
                        role="group"
                        aria-label="Dienstleistung auswählen"
                      >
                        {provider.services.map((item) => (
                          <button
                            type="button"
                            className={`service-choice ${item.id === serviceId ? 'selected' : ''}`}
                            key={item.id}
                            aria-pressed={item.id === serviceId}
                            onClick={() => {
                              if (item.id !== serviceId) {
                                setServiceId(item.id);
                                setSelectedTime('');
                                setSlots([]);
                                setSlotsLoading(true);
                                setSubmitError('');
                              }
                            }}
                          >
                            <span className="choice-radio" />
                            <span>
                              <strong>{item.name}</strong>
                              <small>
                                <Icon
                                  name="clock"
                                  size={14}
                                />
                                {item.duration} Minuten
                              </small>
                            </span>
                            <Icon
                              name="arrow"
                              size={18}
                            />
                          </button>
                        ))}
                      </div>
                    </section>
                    {service && (
                      <section className="panel booking-section">
                        <div className="booking-section-heading">
                          <span className="step-number">02</span>
                          <div>
                            <h2>Wann passt es dir?</h2>
                            <p>
                              {service.name} · {service.duration} Minuten
                            </p>
                          </div>
                        </div>
                        <label className="booking-date">
                          Dein Wunschdatum
                          <input
                            type="date"
                            required
                            min={today()}
                            max="9999-12-31"
                            value={date}
                            onChange={(event) => {
                              setDate(event.target.value);
                              setSelectedTime('');
                              setSlots([]);
                              setSlotsLoading(true);
                              setSubmitError('');
                            }}
                          />
                        </label>
                        <p className="field-hint">
                          Uhrzeiten in {timeZone}. Angezeigt werden nur freie Termine aus der
                          aktuellen Backend-Berechnung.
                        </p>
                        {slotsLoading ? (
                          <p
                            className="loading"
                            role="status"
                          >
                            Freie Termine werden geladen …
                          </p>
                        ) : slotsError ? (
                          <>
                            <Notice>{slotsError}</Notice>
                            <button
                              className="button secondary"
                              type="button"
                              onClick={() => setSlotsRetry((previous) => previous + 1)}
                            >
                              Termine erneut laden
                            </button>
                          </>
                        ) : !date ? (
                          <p className="muted booking-empty">Bitte wähle ein Datum.</p>
                        ) : slots.length === 0 ? (
                          <div
                            className="booking-empty"
                            role="status"
                          >
                            <Icon
                              name="calendar"
                              size={24}
                            />
                            <h3>An diesem Tag ist kein Termin frei.</h3>
                            <p>Wähle einfach ein anderes Datum.</p>
                          </div>
                        ) : (
                          <div
                            className="slot-grid"
                            role="group"
                            aria-label="Freie Uhrzeiten"
                          >
                            {slots.map((time) => (
                              <button
                                key={time}
                                type="button"
                                className={`slot-button ${selectedTime === time ? 'selected' : ''}`}
                                aria-pressed={selectedTime === time}
                                onClick={() => {
                                  setSelectedTime(time);
                                  setSubmitError('');
                                }}
                              >
                                {formatTime(time)}
                              </button>
                            ))}
                          </div>
                        )}
                      </section>
                    )}
                    {selectedTime && service && (
                      <section className="panel booking-section">
                        <div className="booking-section-heading">
                          <span className="step-number">03</span>
                          <div>
                            <h2>Wie dürfen wir dich erreichen?</h2>
                            <p>Deine Daten werden für diese Buchung benötigt.</p>
                          </div>
                        </div>
                        <div className="booking-contact-fields">
                          <label>
                            Dein Name
                            <input
                              autoComplete="name"
                              required
                              maxLength={100}
                              value={customerName}
                              onChange={(event) => setCustomerName(event.target.value)}
                              placeholder="Max Mustermann"
                            />
                          </label>
                          <label>
                            E-Mail-Adresse
                            <input
                              type="email"
                              autoComplete="email"
                              required
                              maxLength={150}
                              value={customerEmail}
                              onChange={(event) => setCustomerEmail(event.target.value)}
                              placeholder="max@beispiel.de"
                            />
                          </label>
                          <label>
                            Telefonnummer
                            <input
                              type="tel"
                              autoComplete="tel"
                              required
                              maxLength={30}
                              value={customerPhone}
                              onChange={(event) => setCustomerPhone(event.target.value)}
                              placeholder="z. B. 0176 12345678"
                            />
                          </label>
                        </div>
                        <div className="booking-summary">
                          <Icon name="calendar" />
                          <div>
                            <strong>{service.name}</strong>
                            <p>
                              {formatDate(date)} · {formatTime(selectedTime)} Uhr ·{' '}
                              {service.duration} Minuten
                            </p>
                          </div>
                        </div>
                        <button
                          className="button primary full"
                          type="submit"
                          disabled={submitting || slotsLoading}
                        >
                          {submitting ? 'Dein Termin wird gebucht …' : 'Termin verbindlich buchen'}
                          {!submitting && (
                            <Icon
                              name="arrow"
                              size={18}
                            />
                          )}
                        </button>
                      </section>
                    )}
                  </fieldset>
                  {submitError && (
                    <div className="booking-submit-error">
                      <Notice>{submitError}</Notice>
                    </div>
                  )}
                </form>
              )}
            </>
          )
        )}
      </main>
      <footer className="booking-footer">
        <Logo />
        <span>Einfach Zeit für das Wesentliche.</span>
      </footer>
    </div>
  );
}
