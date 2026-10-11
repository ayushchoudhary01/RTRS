/** Amounts arrive as BigDecimal strings and are never parsed to number, so
 *  formatting works on the string and only widens it for display */
export function formatAmount(value: string | null | undefined, currency?: string) {
  if (value == null) return '—'

  const n = Number(value)
  if (Number.isNaN(n)) return value

  const formatted = n.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
  return currency ? `${formatted} ${currency}` : formatted
}

export function formatQuantity(value: string | null | undefined) {
  if (value == null) return '—'
  const n = Number(value)
  return Number.isNaN(n) ? value : n.toLocaleString('en-US')
}

export function formatTime(iso: string | null | undefined) {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString('en-GB', { hour12: false })
}

export function formatDateTime(iso: string | null | undefined) {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleString('en-GB', { hour12: false, dateStyle: 'short', timeStyle: 'medium' })
}

/** Elapsed time since an instant, for showing how long a trade has been in flight */
export function elapsedSince(iso: string | null | undefined, now: number) {
  if (!iso) return null
  const started = new Date(iso).getTime()
  if (Number.isNaN(started)) return null

  const seconds = Math.max(0, Math.floor((now - started) / 1000))
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ${seconds % 60}s`
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`
}

export function shortId(id: string | null | undefined, chars = 8) {
  if (!id) return '—'
  return id.length <= chars ? id : `${id.slice(0, chars)}…`
}

export function shortHash(hash: string | null | undefined) {
  if (!hash) return '—'
  if (hash === '0'.repeat(64)) return 'GENESIS'
  return `${hash.slice(0, 6)}…${hash.slice(-4)}`
}

/** Settlement cycle actually applied, derived from the two dates rather than
 *  assumed — the demo profile settles same day, so a hardcoded T+2 would lie */
export function settlementCycleLabel(
  tradeCreatedAt: string | null | undefined,
  settlementDate: string | null | undefined,
) {
  if (!tradeCreatedAt || !settlementDate) return null

  const traded = new Date(tradeCreatedAt)
  const settles = new Date(`${settlementDate}T00:00:00`)
  if (Number.isNaN(traded.getTime()) || Number.isNaN(settles.getTime())) return null

  const tradedDay = Date.UTC(traded.getFullYear(), traded.getMonth(), traded.getDate())
  const settlesDay = Date.UTC(settles.getFullYear(), settles.getMonth(), settles.getDate())
  const days = Math.round((settlesDay - tradedDay) / 86_400_000)

  return days < 0 ? null : `T+${days}`
}

/** Notional, computed the same way every backend service computes it */
export function notionalOf(quantity: string, limitPrice: string) {
  const n = Number(quantity) * Number(limitPrice)
  return Number.isNaN(n) ? '0' : n.toFixed(2)
}
