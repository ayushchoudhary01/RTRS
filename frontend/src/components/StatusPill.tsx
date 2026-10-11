type Tone = 'cleared' | 'pending' | 'rejected' | 'neutral'

const TONE_CLASS: Record<Tone, string> = {
  cleared: 'border-state-cleared/30 bg-state-cleared/10 text-state-cleared',
  pending: 'border-state-pending/30 bg-state-pending/10 text-state-pending',
  rejected: 'border-state-rejected/30 bg-state-rejected/10 text-state-rejected',
  neutral: 'border-surface-border bg-surface-overlay text-content-muted',
}

// Single place that maps backend vocabulary onto a colour, so risk, AML,
// settlement and reconciliation all read consistently even though each service
// uses its own status words
const TONE_BY_STATUS: Record<string, Tone> = {
  // choreography
  PENDING: 'pending',
  RISK_CLEARED: 'pending',
  AML_CLEARED: 'pending',
  APPROVED: 'cleared',
  REJECTED: 'rejected',
  TIMEOUT: 'rejected',
  // risk and AML verdicts
  BREACHED: 'rejected',
  CLEARED: 'cleared',
  FLAGGED: 'rejected',
  // trade lifecycle
  VALIDATED: 'pending',
  RISK_APPROVED: 'pending',
  EXECUTED: 'cleared',
  SETTLING: 'pending',
  SETTLED: 'cleared',
  FAILED: 'rejected',
  // settlement
  CONFIRMED: 'pending',
  CLEARING: 'pending',
  RETRYING: 'pending',
  ESCALATED: 'rejected',
  // reconciliation
  OPEN: 'rejected',
  RESOLVED: 'cleared',
  RUNNING: 'pending',
  COMPLETED: 'cleared',
  // break severity
  CRITICAL: 'rejected',
  HIGH: 'rejected',
  MEDIUM: 'pending',
  // ledger
  DEBIT: 'neutral',
  CREDIT: 'neutral',
}

interface Props {
  status: string | null | undefined
  tone?: Tone
}

export function StatusPill({ status, tone }: Props) {
  if (!status) return <span className="text-content-muted">—</span>

  const resolved = tone ?? TONE_BY_STATUS[status] ?? 'neutral'

  return (
    <span
      className={`inline-flex items-center rounded border px-2 py-0.5 text-xs font-medium ${TONE_CLASS[resolved]}`}
    >
      {status.replace(/_/g, ' ')}
    </span>
  )
}
