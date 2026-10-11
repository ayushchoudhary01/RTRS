import { useSyncExternalStore } from 'react'

/** Remembers the trade and account last opened, so moving between screens does
 *  not throw away what you were looking at. Switching screens is navigation,
 *  not a reset — only submitting a trade or opening a different id replaces it */

const STORAGE_KEY = 'tradeflow.lastViewed'

export type LastViewed = {
  tradeId?: string
  accountId?: string
}

function read(): LastViewed {
  // Storage can throw in a private window or with site data blocked, and the
  // console has to work without it
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const parsed: unknown = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return {}
    const { tradeId, accountId } = parsed as LastViewed
    return {
      tradeId: typeof tradeId === 'string' ? tradeId : undefined,
      accountId: typeof accountId === 'string' ? accountId : undefined,
    }
  } catch {
    return {}
  }
}

let state: LastViewed = read()
const listeners = new Set<() => void>()

export function remember(patch: LastViewed) {
  const next: LastViewed = { ...state, ...patch }
  if (next.tradeId === state.tradeId && next.accountId === state.accountId) return

  state = next
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    // A page that cannot persist still works for the rest of the session
  }
  listeners.forEach((notify) => notify())
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

// The snapshot is only replaced when something actually changed, so the
// reference stays stable and React does not loop
function snapshot() {
  return state
}

export function useLastViewed(): LastViewed {
  return useSyncExternalStore(subscribe, snapshot, snapshot)
}
