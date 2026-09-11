import type { Problem, SessionResponse } from './types'

const BASE = '/api/v1'
const REFRESH_MARGIN_MS = 60_000

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, problem: Problem) {
    super(problem.detail ?? 'Something went wrong. Try again in a moment.')
    this.status = status
    this.code = problem.code ?? 'error'
    this.fieldErrors = problem.errors ?? {}
  }
}

// The access token lives only in memory. The refresh token is an httpOnly cookie the browser holds.
let accessToken: string | null = null
let accessExpiresAt = 0
const listeners = new Set<(session: SessionResponse | null) => void>()

export function setSession(session: SessionResponse | null): SessionResponse | null {
  accessToken = session?.accessToken ?? null
  accessExpiresAt = session ? Date.parse(session.accessTokenExpiresAt) : 0
  listeners.forEach((listener) => listener(session))
  return session
}

export function onSessionChange(listener: (session: SessionResponse | null) => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

let refreshing: Promise<SessionResponse | null> | null = null

/** One refresh at a time, however many requests hit a 401 together. */
export function refreshSession(): Promise<SessionResponse | null> {
  refreshing ??= attemptRefresh().finally(() => {
    refreshing = null
  })
  return refreshing
}

async function attemptRefresh(retried = false): Promise<SessionResponse | null> {
  const response = await fetch(`${BASE}/auth/refresh`, { method: 'POST', credentials: 'include' })
  if (response.ok) {
    return setSession((await response.json()) as SessionResponse)
  }
  const problem = await readProblem(response)
  // Another tab rotated the cookie a moment ago; the browser now holds the new one.
  if (problem.code === 'refresh_race' && !retried) {
    await new Promise((resolve) => setTimeout(resolve, 400))
    return attemptRefresh(true)
  }
  return setSession(null)
}

/** A token good for at least another minute, for long-lived connections such as the live channel. */
export async function freshAccessToken(): Promise<string | null> {
  if (accessToken && accessExpiresAt - Date.now() > REFRESH_MARGIN_MS) {
    return accessToken
  }
  return (await refreshSession())?.accessToken ?? null
}

type RequestOptions = Omit<RequestInit, 'body'> & { json?: unknown }

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await request(path, options, 'application/json')
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/** A file from the API, such as a receipt PDF, fetched with the same session as every other call. */
export async function apiBlob(path: string): Promise<Blob> {
  return (await request(path, {}, '*/*')).blob()
}

async function request(path: string, options: RequestOptions, accept: string): Promise<Response> {
  const { json, headers, ...init } = options
  const send = () =>
    fetch(`${BASE}${path}`, {
      ...init,
      credentials: 'include',
      headers: {
        Accept: accept,
        ...(json === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...headers,
      },
      body: json === undefined ? undefined : JSON.stringify(json),
    })

  let response = await send()
  if (response.status === 401 && accessToken && !path.startsWith('/auth/')) {
    if (await refreshSession()) {
      response = await send()
    }
  }
  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response))
  }
  return response
}

async function readProblem(response: Response): Promise<Problem> {
  try {
    return (await response.json()) as Problem
  } catch {
    return { status: response.status }
  }
}
