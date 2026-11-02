export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

type Options = { method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'; body?: unknown; csrf?: boolean };

async function request(path: string, options: RequestInit): Promise<Response> {
  try {
    return await fetch(`/api${path}`, { ...options, credentials: 'include' });
  } catch {
    throw new ApiError(0, 'Das Backend ist nicht erreichbar. Bitte prüfe, ob Spring Boot auf Port 8080 läuft.');
  }
}

async function check(response: Response, path: string) {
  if (response.ok) return;
  let message = response.status === 403
    ? 'Die Sicherheitsprüfung ist fehlgeschlagen. Bitte lade die Seite neu und versuche es noch einmal.'
    : 'Die Anfrage konnte nicht verarbeitet werden. Bitte versuche es erneut.';
  try {
    const error = await response.json();
    if (typeof error.message === 'string') message = error.message;
  } catch { /* Vite liefert bei einem nicht erreichbaren Backend ggf. keinen JSON-Body. */ }
  if (response.status >= 500) message = `Serverfehler: ${message}`;
  if (response.status === 401 && !path.startsWith('/auth/')) {
    window.location.replace('/login?expired=1');
  }
  throw new ApiError(response.status, message);
}

export async function api<T = void>(path: string, options: Options = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = {};
  // Frisch vor jedem Schreibzugriff: Login und Logout erneuern bzw. löschen CSRF-Tokens.
  if (method !== 'GET' && options.csrf !== false) {
    const response = await request('/auth/csrf', {});
    await check(response, '/auth/csrf');
    const token: { headerName: string; token: string } = await response.json();
    headers[token.headerName] = token.token;
  }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await request(path, {
    method, headers, body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  await check(response, path);
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'Ein unerwarteter Fehler ist aufgetreten.';
}
