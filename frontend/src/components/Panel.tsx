import type { ReactNode } from 'react'

interface PanelProps {
  title?: string
  action?: ReactNode
  children: ReactNode
  className?: string
}

export function Panel({ title, action, children, className = '' }: PanelProps) {
  return (
    <section className={`panel ${className}`}>
      {title && (
        <header className="panel-header flex items-center justify-between gap-3">
          <span>{title}</span>
          {action}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-content-muted">
      <span className="size-3 animate-spin rounded-full border-2 border-surface-border border-t-accent" />
      {label ?? 'Loading'}
    </div>
  )
}

export function EmptyState({ message, hint }: { message: string; hint?: string }) {
  return (
    <div className="py-8 text-center">
      <p className="text-sm text-content-secondary">{message}</p>
      {hint && <p className="mt-1 text-xs text-content-muted">{hint}</p>}
    </div>
  )
}

export function ErrorState({ message }: { message: string }) {
  return (
    <div className="rounded-md border border-state-rejected/30 bg-state-rejected/10 px-3 py-2 text-sm text-state-rejected">
      {message}
    </div>
  )
}

/** Label-value row, used anywhere a record's fields are listed */
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-1.5">
      <dt className="text-xs uppercase tracking-wide text-content-muted">{label}</dt>
      <dd className="text-right text-sm text-content-primary">{children}</dd>
    </div>
  )
}
