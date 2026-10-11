import type { ApiResponse } from './types'

/** Thrown for any non-2xx response, carrying whatever the service told us
 *  Fields are declared explicitly rather than as constructor parameter
 *  properties, which erasableSyntaxOnly disallows */
export class ApiError extends Error {
  readonly status: number
  readonly errorCode: string | null

  constructor(status: number, message: string, errorCode: string | null = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
  }

  /** A missing resource is a normal state here, not a failure — a trade that has
   *  not reached the ledger yet simply has no entries, and callers render that
   *  as "waiting" rather than as an error */
  get isNotFound() {
    return this.status === 404
  }
}

async function parseError(res: Response): Promise<ApiError> {
  let message = `${res.status} ${res.statusText}`
  let errorCode: string | null = null

  try {
    const body = (await res.json()) as Partial<ApiResponse<unknown>> & { message?: string }
    if (body?.message) message = body.message
    if (body?.errorCode) errorCode = body.errorCode
  } catch {
    // Error bodies are not guaranteed to be JSON — keep the status line
  }

  return new ApiError(res.status, message, errorCode)
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })

  if (!res.ok) throw await parseError(res)
  if (res.status === 204) return undefined as T

  return (await res.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
}
