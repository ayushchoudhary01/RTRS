import type { ReactNode } from 'react'

export type StageState = 'done' | 'active' | 'failed' | 'waiting' | 'skipped'

export type StageHue =
  | 'submit'
  | 'risk'
  | 'aml'
  | 'execute'
  | 'ledger'
  | 'settle'
  | 'recon'

const HUE_TEXT: Record<StageHue, string> = {
  submit: 'text-stage-submit',
  risk: 'text-stage-risk',
  aml: 'text-stage-aml',
  execute: 'text-stage-execute',
  ledger: 'text-stage-ledger',
  settle: 'text-stage-settle',
  recon: 'text-stage-recon',
}

const HUE_BORDER: Record<StageHue, string> = {
  submit: 'border-stage-submit/40',
  risk: 'border-stage-risk/40',
  aml: 'border-stage-aml/40',
  execute: 'border-stage-execute/40',
  ledger: 'border-stage-ledger/40',
  settle: 'border-stage-settle/40',
  recon: 'border-stage-recon/40',
}

const HUE_GLOW: Record<StageHue, string> = {
  submit: 'bg-stage-submit/10',
  risk: 'bg-stage-risk/10',
  aml: 'bg-stage-aml/10',
  execute: 'bg-stage-execute/10',
  ledger: 'bg-stage-ledger/10',
  settle: 'bg-stage-settle/10',
  recon: 'bg-stage-recon/10',
}

const MARKER: Record<StageState, string> = {
  done: '✓',
  active: '',
  failed: '✕',
  waiting: '',
  skipped: '–',
}

interface Props {
  hue: StageHue
  title: string
  state: StageState
  summary?: ReactNode
  children?: ReactNode
  /** Drawn below the node, connecting to the next stage */
  connector?: 'none' | 'idle' | 'flowing' | 'done' | 'failed'
}

export function PipelineStage({
  hue,
  title,
  state,
  summary,
  children,
  connector = 'none',
}: Props) {
  const dim = state === 'waiting' || state === 'skipped'

  return (
    <div className="flex gap-3">
      {/* Rail — the node plus the connector down to the next stage */}
      <div className="flex w-7 shrink-0 flex-col items-center">
        <div
          className={`flex size-7 items-center justify-center rounded-full border text-xs font-semibold
            ${dim ? 'border-surface-border text-content-muted' : `${HUE_BORDER[hue]} ${HUE_GLOW[hue]} ${HUE_TEXT[hue]}`}
            ${state === 'failed' ? 'border-state-rejected/50 bg-state-rejected/10 text-state-rejected' : ''}
            ${state === 'active' ? 'animate-pulse-ring' : ''}`}
        >
          {state === 'active' ? (
            <span className="size-2 rounded-full bg-current" />
          ) : (
            MARKER[state]
          )}
        </div>

        {connector !== 'none' && (
          <div className="relative w-px flex-1 overflow-hidden">
            {connector === 'flowing' ? (
              <div
                className="animate-flow h-full w-px"
                style={{
                  backgroundImage:
                    'repeating-linear-gradient(to bottom, currentColor 0 4px, transparent 4px 8px)',
                  color: 'var(--color-state-pending)',
                }}
              />
            ) : (
              <div
                className={`h-full w-px ${
                  connector === 'done'
                    ? 'bg-state-cleared/40'
                    : connector === 'failed'
                      ? 'bg-state-rejected/30'
                      : 'bg-surface-border'
                }`}
              />
            )}
          </div>
        )}
      </div>

      <div className={`min-w-0 flex-1 pb-5 ${dim ? 'opacity-55' : ''}`}>
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <h3 className={`text-sm font-medium ${dim ? 'text-content-secondary' : HUE_TEXT[hue]}`}>
            {title}
          </h3>
          {summary}
        </div>
        {children && <div className="mt-2">{children}</div>}
      </div>
    </div>
  )
}

/** Compact key-value grid used inside a stage body */
export function StageDetails({ items }: { items: Array<[string, ReactNode]> }) {
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded-lg border border-surface-border/70 bg-surface-base/40 px-3 py-2">
      {items.map(([label, value]) => (
        <div key={label} className="contents">
          <dt className="text-xs text-content-muted">{label}</dt>
          <dd className="min-w-0 break-words text-right text-xs text-content-secondary">{value}</dd>
        </div>
      ))}
    </dl>
  )
}
