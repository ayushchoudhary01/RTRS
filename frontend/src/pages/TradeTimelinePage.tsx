import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  useAmlEvaluation,
  useApprovalState,
  useLedgerEntriesByTrade,
  useRiskEvaluation,
  useSettlementInstruction,
  useTrade,
} from '../api/hooks'
import { CopyableId, EmptyState, ErrorState, Panel, Spinner } from '../components/Panel'
import { PipelineStage, StageDetails, type StageState } from '../components/PipelineStage'
import { StatusPill } from '../components/StatusPill'
import { elapsedSince, formatAmount, formatTime, shortHash } from '../lib/format'

export function TradeTimelinePage() {
  const { tradeId } = useParams()
  const navigate = useNavigate()
  const [input, setInput] = useState(tradeId ?? '')
  const [now, setNow] = useState(() => Date.now())

  // Drives the elapsed-time readout while a trade is still moving
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])

  const trade = useTrade(tradeId)
  const approval = useApprovalState(tradeId, { live: true })
  const risk = useRiskEvaluation(tradeId, { live: true })
  const aml = useAmlEvaluation(tradeId, { live: true })
  const ledger = useLedgerEntriesByTrade(tradeId, { live: true })
  const settlement = useSettlementInstruction(tradeId, { live: true })

  if (!tradeId) {
    return (
      <Panel title="Trade timeline" subtitle="Follow a trade across all seven services">
        <form
          className="flex flex-col gap-2 sm:flex-row"
          onSubmit={(e) => {
            e.preventDefault()
            if (input.trim()) navigate(`/trades/${input.trim()}`)
          }}
        >
          <input
            className="field-input numeric"
            placeholder="Trade ID (UUID)"
            value={input}
            onChange={(e) => setInput(e.target.value)}
          />
          <button type="submit" className="btn-primary shrink-0">
            Follow
          </button>
        </form>
        <EmptyState
          message="No trade selected"
          hint="Submit a trade and you will land here automatically"
        />
      </Panel>
    )
  }

  if (trade.isLoading) {
    return (
      <Panel title="Trade timeline">
        <Spinner label="Loading trade" />
      </Panel>
    )
  }

  if (trade.isError) {
    return (
      <Panel title="Trade timeline">
        <ErrorState message={(trade.error as Error).message} />
      </Panel>
    )
  }

  const a = approval.data
  const r = risk.data
  const m = aml.data
  const entries = ledger.data ?? []
  const s = settlement.data

  const rejected = a?.status === 'REJECTED' || a?.status === 'TIMEOUT'
  const executed = a?.status === 'APPROVED'

  // Risk and AML run in parallel and either can arrive first, which is the whole
  // point of the choreography — so each gets its own state, never a shared one
  const riskState: StageState = r ? (r.outcome === 'APPROVED' ? 'done' : 'failed') : 'active'
  const amlState: StageState = m ? (m.outcome === 'CLEARED' ? 'done' : 'failed') : 'active'

  const executeState: StageState = executed ? 'done' : rejected ? 'failed' : 'active'
  const ledgerState: StageState = entries.length
    ? 'done'
    : rejected
      ? 'skipped'
      : executed
        ? 'active'
        : 'waiting'
  const settleState: StageState = s
    ? s.status === 'SETTLED'
      ? 'done'
      : s.status === 'ESCALATED'
        ? 'failed'
        : 'active'
    : rejected
      ? 'skipped'
      : executed
        ? 'active'
        : 'waiting'

  const elapsed = elapsedSince(trade.data?.createdAt, now)
  const live = !rejected && (!s || s.status !== 'SETTLED')

  return (
    <div className="grid gap-5 lg:grid-cols-[1fr_20rem]">
      <Panel
        title="Pipeline"
        subtitle={`Trade ${trade.data?.clientOrderRef ?? ''}`}
        action={
          live ? (
            <span className="flex items-center gap-1.5 text-xs text-state-pending">
              <span className="size-1.5 animate-pulse rounded-full bg-current" />
              live · {elapsed}
            </span>
          ) : (
            <span className="text-xs text-content-muted">settled · {elapsed}</span>
          )
        }
      >
        <PipelineStage
          hue="submit"
          title="Submitted"
          state="done"
          summary={<StatusPill status={trade.data?.status} />}
          connector={a ? 'done' : 'flowing'}
        >
          <StageDetails
            items={[
              ['Instrument', `${trade.data?.instrumentId} · ${trade.data?.tradeType}`],
              [
                'Notional',
                formatAmount(
                  String(Number(trade.data?.quantity) * Number(trade.data?.limitPrice)),
                  trade.data?.currency,
                ),
              ],
              ['Accepted', formatTime(trade.data?.createdAt)],
            ]}
          />
        </PipelineStage>

        <PipelineStage
          hue="risk"
          title="Risk evaluation"
          state={riskState}
          summary={
            r ? (
              <StatusPill status={r.outcome} />
            ) : (
              <span className="text-xs text-content-muted">waiting for verdict</span>
            )
          }
          connector={r ? (r.outcome === 'APPROVED' ? 'done' : 'failed') : 'flowing'}
        >
          {r && (
            <StageDetails
              items={[
                ['Notional checked', formatAmount(r.notionalUsd, 'USD')],
                ...(r.breachedRule
                  ? ([
                      ['Breached rule', <span className="text-state-rejected">{r.breachedRule}</span>],
                      ['Reason', r.breachReason],
                    ] as Array<[string, React.ReactNode]>)
                  : []),
                ['Evaluated', formatTime(r.evaluatedAt)],
              ]}
            />
          )}
        </PipelineStage>

        <PipelineStage
          hue="aml"
          title="AML screening"
          state={amlState}
          summary={
            m ? (
              <>
                <StatusPill status={m.outcome} />
                <span className="numeric text-xs text-content-muted">score {m.riskScore}</span>
              </>
            ) : (
              <span className="text-xs text-content-muted">waiting for verdict</span>
            )
          }
          connector={m ? (m.outcome === 'CLEARED' ? 'done' : 'failed') : 'flowing'}
        >
          {m && (
            <StageDetails
              items={[
                ...(m.flaggedRule
                  ? ([
                      ['Flagged rule', <span className="text-state-rejected">{m.flaggedRule}</span>],
                      ['Reason', m.flaggedReason],
                      ['Manual review', m.requiresManualReview ? 'Required' : 'Not required'],
                    ] as Array<[string, React.ReactNode]>)
                  : ([['Rules passed', 'All five typologies cleared']] as Array<
                      [string, React.ReactNode]
                    >)),
                ['Evaluated', formatTime(m.evaluatedAt)],
              ]}
            />
          )}
        </PipelineStage>

        <PipelineStage
          hue="execute"
          title="Execution"
          state={executeState}
          summary={
            <>
              <StatusPill status={a?.status} />
              {!a && <span className="text-xs text-content-muted">approval row not created yet</span>}
            </>
          }
          connector={executed ? 'done' : rejected ? 'failed' : 'flowing'}
        >
          {a && (
            <StageDetails
              items={[
                [
                  'Clearances',
                  <span className="inline-flex gap-2">
                    <span className={a.riskCleared ? 'text-state-cleared' : 'text-content-muted'}>
                      {a.riskCleared ? '✓' : '○'} risk
                    </span>
                    <span className={a.amlCleared ? 'text-state-cleared' : 'text-content-muted'}>
                      {a.amlCleared ? '✓' : '○'} aml
                    </span>
                  </span>,
                ],
                ...(a.rejectionReason
                  ? ([['Rejection', <span className="text-state-rejected">{a.rejectionReason}</span>]] as Array<
                      [string, React.ReactNode]
                    >)
                  : []),
                ['Updated', formatTime(a.updatedAt)],
              ]}
            />
          )}
        </PipelineStage>

        <PipelineStage
          hue="ledger"
          title="Ledger posting"
          state={ledgerState}
          summary={
            entries.length ? (
              <span className="text-xs text-content-muted">
                {entries.length} entries · double-entry balanced
              </span>
            ) : rejected ? (
              <span className="text-xs text-content-muted">skipped — trade never executed</span>
            ) : null
          }
          connector={entries.length ? 'done' : rejected ? 'failed' : 'flowing'}
        >
          {entries.length > 0 && (
            <div className="space-y-1.5">
              {entries.map((e) => (
                <div
                  key={e.entryId}
                  className="flex items-center justify-between gap-3 rounded-lg border border-surface-border/70 bg-surface-base/40 px-3 py-2"
                >
                  <span
                    className={`text-xs font-medium ${
                      e.entryType === 'DEBIT' ? 'text-stage-aml' : 'text-stage-ledger'
                    }`}
                  >
                    {e.entryType}
                  </span>
                  <span className="numeric text-xs text-content-primary">
                    {formatAmount(e.amount, e.currency)}
                  </span>
                  <span
                    className="numeric text-[10px] text-content-muted"
                    title={`${e.prevHash} → ${e.entryHash}`}
                  >
                    {shortHash(e.prevHash)} → {shortHash(e.entryHash)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </PipelineStage>

        <PipelineStage
          hue="settle"
          title="Settlement"
          state={settleState}
          summary={
            s ? (
              <>
                <StatusPill status={s.status} />
                {s.retryCount > 0 && (
                  <span className="text-xs text-state-pending">retry {s.retryCount}</span>
                )}
              </>
            ) : rejected ? (
              <span className="text-xs text-content-muted">skipped</span>
            ) : null
          }
          connector="none"
        >
          {s && (
            <StageDetails
              items={[
                ['Settlement date', `${s.settlementDate} (T+2)`],
                ...(s.failureReason
                  ? ([['Failure', <span className="text-state-rejected">{s.failureReason}</span>]] as Array<
                      [string, React.ReactNode]
                    >)
                  : []),
              ]}
            />
          )}
        </PipelineStage>
      </Panel>

      <div className="space-y-5">
        <Panel title="Trade">
          <dl className="space-y-1">
            <div className="flex items-baseline justify-between gap-3 py-1">
              <dt className="text-xs uppercase tracking-wide text-content-muted">Trade ID</dt>
              <dd>
                <CopyableId value={tradeId} />
              </dd>
            </div>
            <div className="flex items-baseline justify-between gap-3 py-1">
              <dt className="text-xs uppercase tracking-wide text-content-muted">Account</dt>
              <dd>
                <CopyableId value={trade.data?.accountId} />
              </dd>
            </div>
          </dl>
          <button
            type="button"
            className="btn-ghost mt-3 w-full"
            onClick={() => navigate(`/ledger/${trade.data?.accountId}`)}
            disabled={!trade.data?.accountId}
          >
            View account ledger
          </button>
        </Panel>

        <Panel title="Why this shape" bodyClassName="px-4 py-3">
          <p className="text-xs leading-relaxed text-content-secondary">
            Risk and AML consume the same event independently and publish verdicts in
            whichever order they finish, so each shows its own state. Execution happens
            only once both clear. A rejection is the <em>absence</em> of a clearance,
            found by the 30-second timeout rather than by a dedicated consumer.
          </p>
        </Panel>
      </div>
    </div>
  )
}
