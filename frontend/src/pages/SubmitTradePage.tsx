import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useSubmitTrade } from '../api/hooks'
import type { TradeRequest } from '../api/types'
import { ErrorState, Panel } from '../components/Panel'
import { formatAmount, notionalOf } from '../lib/format'

const DEMO_ACCOUNT = '550e8460-e29b-41d4-a716-446655440000'

/** Each preset lands on the far side of exactly one rule threshold, so the
 *  timeline can be watched taking a different path each time
 *  Thresholds come from the services' own config, not from guesses */
const PRESETS = [
  {
    name: 'Clean execution',
    note: 'Passes every risk and AML rule, runs the full pipeline',
    tone: 'cleared' as const,
    quantity: '137',
    limitPrice: '173.42',
  },
  {
    name: 'AML — round amount',
    note: 'Exactly $66,000, an even multiple of 1,000 above the $50k floor',
    tone: 'rejected' as const,
    quantity: '440',
    limitPrice: '150.00',
  },
  {
    name: 'AML — structuring',
    note: '$9,000, inside the $8k–$10k band just under the reporting threshold',
    tone: 'rejected' as const,
    quantity: '60',
    limitPrice: '150.00',
  },
  {
    name: 'Risk — VaR limit',
    note: '$825,000 — 2% of notional exceeds the $15k VaR tolerance, under the position limit',
    tone: 'rejected' as const,
    quantity: '5500',
    limitPrice: '150.00',
  },
  {
    name: 'Risk + AML — both breach',
    note: '$1.5M, over the $1M position limit and the $500k high-value threshold',
    tone: 'rejected' as const,
    quantity: '10000',
    limitPrice: '150.00',
  },
]

const TONE_RING = {
  cleared: 'hover:border-state-cleared/60 hover:bg-state-cleared/5',
  rejected: 'hover:border-state-rejected/50 hover:bg-state-rejected/5',
}

function newClientOrderRef() {
  return `ORDER-${Date.now().toString(36).toUpperCase()}`
}

export function SubmitTradePage() {
  const navigate = useNavigate()
  const submit = useSubmitTrade()

  const [form, setForm] = useState<TradeRequest>({
    clientOrderRef: newClientOrderRef(),
    accountId: DEMO_ACCOUNT,
    instrumentId: 'AAPL',
    tradeType: 'BUY',
    quantity: '137',
    limitPrice: '173.42',
    currency: 'USD',
    market: 'NASDAQ',
  })

  const set = <K extends keyof TradeRequest>(key: K, value: TradeRequest[K]) =>
    setForm((f) => ({ ...f, [key]: value }))

  const applyPreset = (p: (typeof PRESETS)[number]) =>
    setForm((f) => ({
      ...f,
      clientOrderRef: newClientOrderRef(),
      quantity: p.quantity,
      limitPrice: p.limitPrice,
    }))

  const notional = notionalOf(form.quantity, form.limitPrice)

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    submit.mutate(form, {
      onSuccess: (res) => {
        const id = res.data?.tradeId
        if (id) navigate(`/trades/${id}`)
      },
    })
  }

  const error = submit.error
  const errorMessage =
    error instanceof ApiError ? error.message : error ? String(error) : null

  return (
    <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
      <Panel title="Submit a trade" subtitle="Enters the pipeline at trade-ingestion">
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="field-label" htmlFor="clientOrderRef">
                Client order ref
              </label>
              <div className="flex gap-2">
                <input
                  id="clientOrderRef"
                  className="field-input numeric"
                  value={form.clientOrderRef}
                  onChange={(e) => set('clientOrderRef', e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="btn-ghost shrink-0 px-3"
                  onClick={() => set('clientOrderRef', newClientOrderRef())}
                  title="Reused refs are rejected by the Redis idempotency check"
                >
                  ↻
                </button>
              </div>
            </div>

            <div>
              <label className="field-label" htmlFor="instrumentId">
                Instrument
              </label>
              <input
                id="instrumentId"
                className="field-input"
                value={form.instrumentId}
                onChange={(e) => set('instrumentId', e.target.value.toUpperCase())}
                required
              />
            </div>

            <div>
              <label className="field-label" htmlFor="tradeType">
                Side
              </label>
              <select
                id="tradeType"
                className="field-input"
                value={form.tradeType}
                onChange={(e) => set('tradeType', e.target.value as TradeRequest['tradeType'])}
              >
                <option value="BUY">BUY</option>
                <option value="SELL">SELL</option>
              </select>
            </div>

            <div>
              <label className="field-label" htmlFor="market">
                Market
              </label>
              <input
                id="market"
                className="field-input"
                value={form.market}
                onChange={(e) => set('market', e.target.value)}
                required
              />
            </div>

            <div>
              <label className="field-label" htmlFor="quantity">
                Quantity
              </label>
              <input
                id="quantity"
                className="field-input numeric"
                inputMode="decimal"
                value={form.quantity}
                onChange={(e) => set('quantity', e.target.value)}
                required
              />
            </div>

            <div>
              <label className="field-label" htmlFor="limitPrice">
                Limit price
              </label>
              <input
                id="limitPrice"
                className="field-input numeric"
                inputMode="decimal"
                value={form.limitPrice}
                onChange={(e) => set('limitPrice', e.target.value)}
                required
              />
            </div>

            <div className="sm:col-span-2">
              <label className="field-label" htmlFor="accountId">
                Account
              </label>
              <input
                id="accountId"
                className="field-input numeric text-xs"
                value={form.accountId}
                onChange={(e) => set('accountId', e.target.value)}
                required
              />
            </div>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-surface-border bg-surface-base/50 px-4 py-3">
            <div>
              <span className="text-xs uppercase tracking-wide text-content-muted">Notional</span>
              <p className="numeric text-lg text-content-primary">
                {formatAmount(notional, form.currency)}
              </p>
            </div>
            <button type="submit" className="btn-primary" disabled={submit.isPending}>
              {submit.isPending ? 'Submitting' : 'Submit trade'}
            </button>
          </div>

          {errorMessage && <ErrorState message={errorMessage} />}
        </form>
      </Panel>

      <Panel title="Presets" subtitle="Each one crosses a different rule threshold">
        <div className="space-y-2">
          {PRESETS.map((p) => (
            <button
              key={p.name}
              type="button"
              onClick={() => applyPreset(p)}
              className={`w-full rounded-lg border border-surface-border bg-surface-base/40 px-3 py-2.5
                          text-left transition-all active:scale-[0.99] ${TONE_RING[p.tone]}`}
            >
              <div className="flex items-baseline justify-between gap-2">
                <span className="text-sm text-content-primary">{p.name}</span>
                <span className="numeric text-xs text-content-muted">
                  {formatAmount(notionalOf(p.quantity, p.limitPrice))}
                </span>
              </div>
              <p className="mt-0.5 text-xs leading-snug text-content-muted">{p.note}</p>
            </button>
          ))}
        </div>
      </Panel>
    </div>
  )
}
