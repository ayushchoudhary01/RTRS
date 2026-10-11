import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useBreaks, useReconciliationRuns } from '../api/hooks'
import { CopyableId, EmptyState, ErrorState, Panel, Spinner } from '../components/Panel'
import { StatusPill } from '../components/StatusPill'
import { formatDateTime, shortId } from '../lib/format'

const SEVERITY_BAR: Record<string, string> = {
  CRITICAL: 'bg-state-rejected',
  HIGH: 'bg-state-pending',
  MEDIUM: 'bg-stage-settle',
}

export function BreaksPage() {
  const [page, setPage] = useState(0)
  const breaks = useBreaks(page)
  const runs = useReconciliationRuns(0)

  const rows = breaks.data?.content ?? []
  const open = rows.filter((b) => b.status === 'OPEN').length
  const latestRun = runs.data?.content?.[0]

  return (
    <div className="space-y-5">
      <div className="grid gap-5 sm:grid-cols-3">
        <Panel title="Open breaks" bodyClassName="px-4 py-3">
          {/* A failed query must never render as a reassuring zero — not knowing
              and knowing there are none are very different answers here */}
          {breaks.isError ? (
            <>
              <p className="numeric text-2xl text-content-muted">?</p>
              <p className="mt-1 text-xs text-state-rejected">
                reconciliation-service unreachable
              </p>
            </>
          ) : (
            <>
              <p
                className={`numeric text-2xl ${
                  breaks.isLoading
                    ? 'text-content-muted'
                    : open > 0
                      ? 'text-state-rejected'
                      : 'text-state-cleared'
                }`}
              >
                {breaks.isLoading ? '—' : open}
              </p>
              <p className="mt-1 text-xs text-content-muted">
                {breaks.isLoading
                  ? 'checking'
                  : open === 0
                    ? 'ledger and trade records agree'
                    : 'awaiting the next pass'}
              </p>
            </>
          )}
        </Panel>

        <Panel title="Last run" bodyClassName="px-4 py-3">
          {runs.isError ? (
            <p className="text-sm text-state-rejected">Unavailable</p>
          ) : latestRun ? (
            <>
              <div className="flex items-center gap-2">
                <StatusPill status={latestRun.status} />
                {latestRun.durationMs != null && (
                  <span className="numeric text-xs text-content-muted">
                    {latestRun.durationMs}ms
                  </span>
                )}
              </div>
              <p className="mt-1.5 text-xs text-content-muted">
                {latestRun.tradesChecked} checked · {latestRun.breaksFound} found ·{' '}
                {latestRun.breaksResolved} self-healed
              </p>
            </>
          ) : (
            <p className="text-sm text-content-muted">No runs yet</p>
          )}
        </Panel>

        <Panel title="How it heals" bodyClassName="px-4 py-3">
          <p className="text-xs leading-relaxed text-content-secondary">
            Every pass re-checks broken facts first. A break caused by a late ledger
            entry closes itself once the data arrives — nothing is resolved by hand.
          </p>
        </Panel>
      </div>

      <Panel
        title="Breaks"
        subtitle="Detected by comparing trade execution records against ledger entries"
        action={
          <span className="flex items-center gap-1.5 text-xs text-content-muted">
            <span className="size-1.5 animate-pulse rounded-full bg-state-pending" />
            refreshing
          </span>
        }
        bodyClassName="p-0"
      >
        {breaks.isLoading ? (
          <div className="p-4">
            <Spinner label="Loading breaks" />
          </div>
        ) : breaks.isError ? (
          <div className="p-4">
            <ErrorState message={(breaks.error as Error).message} />
          </div>
        ) : rows.length === 0 ? (
          <EmptyState
            message="No breaks detected"
            hint="Reconciliation found the ledger and the trade records consistent"
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-surface-border text-xs uppercase tracking-wide text-content-muted">
                <tr>
                  <th className="px-4 py-2 font-medium">Severity</th>
                  <th className="px-4 py-2 font-medium">Break</th>
                  <th className="px-4 py-2 font-medium">Trade</th>
                  <th className="px-4 py-2 font-medium">Expected</th>
                  <th className="px-4 py-2 font-medium">Actual</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  <th className="px-4 py-2 font-medium">Detected</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((b) => (
                  <tr key={b.id} className="data-row">
                    <td className="px-4 py-2">
                      <span className="flex items-center gap-2">
                        <span
                          className={`h-4 w-1 rounded-full ${SEVERITY_BAR[b.severity] ?? 'bg-surface-border'}`}
                        />
                        <span className="text-xs text-content-secondary">{b.severity}</span>
                      </span>
                    </td>
                    <td className="px-4 py-2 text-xs text-content-primary">
                      {b.breakType.replace(/_/g, ' ')}
                    </td>
                    <td className="px-4 py-2">
                      <Link
                        to={`/trades/${b.tradeId}`}
                        className="numeric text-xs text-accent hover:underline"
                      >
                        {shortId(b.tradeId)}
                      </Link>
                    </td>
                    <td className="numeric px-4 py-2 text-xs text-content-muted">
                      {b.expectedValue ?? '—'}
                    </td>
                    <td className="numeric px-4 py-2 text-xs text-content-muted">
                      {b.actualValue ?? '—'}
                    </td>
                    <td className="px-4 py-2">
                      <StatusPill status={b.status} />
                    </td>
                    <td className="numeric px-4 py-2 text-xs text-content-muted">
                      {formatDateTime(b.detectedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {breaks.data && breaks.data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-surface-border px-4 py-2.5">
            <span className="text-xs text-content-muted">
              Page {breaks.data.number + 1} of {breaks.data.totalPages}
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                className="btn-ghost px-3 py-1 text-xs"
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                Previous
              </button>
              <button
                type="button"
                className="btn-ghost px-3 py-1 text-xs"
                onClick={() => setPage((p) => p + 1)}
                disabled={page + 1 >= breaks.data.totalPages}
              >
                Next
              </button>
            </div>
          </div>
        )}
      </Panel>

      <Panel title="Recent runs" bodyClassName="p-0">
        {runs.isError ? (
          <div className="p-4">
            <ErrorState message={(runs.error as Error).message} />
          </div>
        ) : runs.data?.content?.length ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-surface-border text-xs uppercase tracking-wide text-content-muted">
                <tr>
                  <th className="px-4 py-2 font-medium">Run</th>
                  <th className="px-4 py-2 font-medium">Status</th>
                  <th className="px-4 py-2 text-right font-medium">Checked</th>
                  <th className="px-4 py-2 text-right font-medium">Found</th>
                  <th className="px-4 py-2 text-right font-medium">Resolved</th>
                  <th className="px-4 py-2 font-medium">Started</th>
                </tr>
              </thead>
              <tbody>
                {runs.data.content.map((run) => (
                  <tr key={run.id} className="data-row">
                    <td className="px-4 py-2">
                      <CopyableId value={run.id} label={shortId(run.id)} />
                    </td>
                    <td className="px-4 py-2">
                      <StatusPill status={run.status} />
                    </td>
                    <td className="numeric px-4 py-2 text-right text-xs">{run.tradesChecked}</td>
                    <td className="numeric px-4 py-2 text-right text-xs text-state-rejected">
                      {run.breaksFound || '—'}
                    </td>
                    <td className="numeric px-4 py-2 text-right text-xs text-state-cleared">
                      {run.breaksResolved || '—'}
                    </td>
                    <td className="numeric px-4 py-2 text-xs text-content-muted">
                      {formatDateTime(run.startedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState message="No reconciliation runs yet" />
        )}
      </Panel>
    </div>
  )
}
