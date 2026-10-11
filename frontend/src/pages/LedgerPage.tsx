import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  useAccountBalance,
  useLedgerEntriesByAccount,
  useVerifyChainIntegrity,
} from '../api/hooks'
import { CopyableId, EmptyState, ErrorState, Panel, Spinner } from '../components/Panel'
import { formatAmount, formatDateTime, shortHash } from '../lib/format'

export function LedgerPage() {
  const { accountId } = useParams()
  const navigate = useNavigate()
  const [input, setInput] = useState(accountId ?? '')

  const balance = useAccountBalance(accountId)
  const entries = useLedgerEntriesByAccount(accountId)
  const verify = useVerifyChainIntegrity()

  if (!accountId) {
    return (
      <Panel title="Ledger" subtitle="Append-only double-entry record with a SHA-256 hash chain">
        <form
          className="flex flex-col gap-2 sm:flex-row"
          onSubmit={(e) => {
            e.preventDefault()
            if (input.trim()) navigate(`/ledger/${input.trim()}`)
          }}
        >
          <input
            className="field-input numeric"
            placeholder="Account ID (UUID)"
            value={input}
            onChange={(e) => setInput(e.target.value)}
          />
          <button type="submit" className="btn-primary shrink-0">
            Open
          </button>
        </form>
        <EmptyState message="No account selected" />
      </Panel>
    )
  }

  const rows = entries.data ?? []
  const verdict = verify.data as { valid?: boolean; intact?: boolean } | undefined
  const chainOk = verdict?.valid ?? verdict?.intact

  return (
    <div className="space-y-5">
      <div className="grid gap-5 sm:grid-cols-3">
        <Panel title="Balance" bodyClassName="px-4 py-3">
          {balance.isLoading ? (
            <Spinner />
          ) : balance.data ? (
            <>
              <p className="numeric text-2xl text-content-primary">
                {formatAmount(balance.data.balance, balance.data.currency)}
              </p>
              <p className="mt-1 text-xs text-content-muted">
                net of {balance.data.entryCount} entries
              </p>
            </>
          ) : (
            <p className="text-sm text-content-muted">No balance yet</p>
          )}
        </Panel>

        <Panel title="Chain head" bodyClassName="px-4 py-3">
          <p
            className="numeric truncate text-sm text-stage-ledger"
            title={balance.data?.lastEntryHash ?? ''}
          >
            {shortHash(balance.data?.lastEntryHash)}
          </p>
          <p className="mt-1 text-xs text-content-muted">
            {formatDateTime(balance.data?.lastEntryAt)}
          </p>
        </Panel>

        <Panel title="Integrity" bodyClassName="px-4 py-3">
          <button
            type="button"
            className="btn-primary w-full"
            onClick={() => verify.mutate(accountId)}
            disabled={verify.isPending}
          >
            {verify.isPending ? 'Verifying' : 'Verify chain'}
          </button>
          {verify.isSuccess && (
            <p
              className={`mt-2 text-xs ${chainOk === false ? 'text-state-rejected' : 'text-state-cleared'}`}
            >
              {chainOk === false
                ? '✕ chain broken — a row was altered'
                : '✓ every hash recomputed and matched'}
            </p>
          )}
          {verify.isError && (
            <p className="mt-2 text-xs text-state-rejected">
              {(verify.error as Error).message}
            </p>
          )}
        </Panel>
      </div>

      <Panel
        title="Entries"
        subtitle="Each entry hashes the one before it, so any edit breaks every link after it"
        action={<CopyableId value={accountId} />}
        bodyClassName="p-0"
      >
        {entries.isLoading ? (
          <div className="p-4">
            <Spinner label="Loading entries" />
          </div>
        ) : entries.isError ? (
          <div className="p-4">
            <ErrorState message={(entries.error as Error).message} />
          </div>
        ) : rows.length === 0 ? (
          <EmptyState message="No ledger entries for this account" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-surface-border text-xs uppercase tracking-wide text-content-muted">
                <tr>
                  <th className="px-4 py-2 font-medium">#</th>
                  <th className="px-4 py-2 font-medium">Type</th>
                  <th className="px-4 py-2 text-right font-medium">Amount</th>
                  <th className="px-4 py-2 text-right font-medium">Balance after</th>
                  <th className="px-4 py-2 font-medium">Chain</th>
                  <th className="px-4 py-2 font-medium">Created</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((e) => (
                  <tr key={e.entryId} className="data-row">
                    <td className="numeric px-4 py-2 text-xs text-content-muted">
                      {e.sequenceNum}
                    </td>
                    <td className="px-4 py-2">
                      <span
                        className={`text-xs font-medium ${
                          e.entryType === 'DEBIT' ? 'text-stage-aml' : 'text-stage-ledger'
                        }`}
                      >
                        {e.entryType}
                      </span>
                    </td>
                    <td className="numeric px-4 py-2 text-right text-content-primary">
                      {formatAmount(e.amount)}
                    </td>
                    <td className="numeric px-4 py-2 text-right text-content-secondary">
                      {formatAmount(e.balanceAfter)}
                    </td>
                    <td className="px-4 py-2">
                      <span
                        className="numeric text-[11px] text-content-muted"
                        title={`${e.prevHash} → ${e.entryHash}`}
                      >
                        {shortHash(e.prevHash)}
                        <span className="mx-1 text-stage-ledger">→</span>
                        {shortHash(e.entryHash)}
                      </span>
                    </td>
                    <td className="numeric px-4 py-2 text-xs text-content-muted">
                      {formatDateTime(e.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  )
}
