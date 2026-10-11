import { useState, type ReactNode } from 'react'

interface PanelProps {
  title?: string
  subtitle?: string
  action?: ReactNode
  children: ReactNode
  className?: string
  bodyClassName?: string
}

export function Panel({
  title,
  subtitle,
  action,
  children,
  className = '',
  bodyClassName = 'p-4',
}: PanelProps) {
  return (
    <section className={`panel animate-rise ${className}`}>
      {title && (
        <header className="panel-header">
          <div className="min-w-0">
            <span className="text-content-primary">{title}</span>
            {subtitle && <p className="mt-0.5 text-xs text-content-muted">{subtitle}</p>}
          </div>
          {action}
        </header>
      )}
      <div className={bodyClassName}>{children}</div>
    </section>
  )
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-content-muted">
      <span className="size-3.5 animate-spin rounded-full border-2 border-surface-border border-t-accent" />
      {label ?? 'Loading'}
    </div>
  )
}

export function EmptyState({ message, hint }: { message: string; hint?: ReactNode }) {
  return (
    <div className="py-10 text-center">
      <p className="text-sm text-content-secondary">{message}</p>
      {hint && <div className="mt-1.5 text-xs text-content-muted">{hint}</div>}
    </div>
  )
}

export function ErrorState({ message }: { message: string }) {
  return (
    <div className="flex items-start gap-2 rounded-lg border border-state-rejected/30 bg-state-rejected/10 px-3 py-2.5 text-sm text-state-rejected">
      <span aria-hidden>⚠</span>
      <span className="min-w-0 break-words">{message}</span>
    </div>
  )
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-4 py-1.5">
      <dt className="shrink-0 text-xs uppercase tracking-wide text-content-muted">{label}</dt>
      <dd className="min-w-0 text-right text-sm text-content-primary">{children}</dd>
    </div>
  )
}

/** UUIDs get copied into curl commands and psql constantly, so every one shown is
 *  click-to-copy rather than something to select by hand */
export function CopyableId({ value, label }: { value: string | null | undefined; label?: string }) {
  const [copied, setCopied] = useState(false)

  if (!value) return <span className="text-content-muted">—</span>

  const copy = () => {
    void navigator.clipboard?.writeText(value).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1200)
    })
  }

  return (
    <button
      type="button"
      onClick={copy}
      title={value}
      className="numeric group inline-flex items-center gap-1.5 rounded px-1 py-0.5 text-xs
                 text-content-secondary transition-colors hover:bg-surface-overlay hover:text-content-primary"
    >
      {label ?? value}
      <span
        className={`text-[10px] transition-opacity ${
          copied ? 'text-state-cleared opacity-100' : 'opacity-0 group-hover:opacity-60'
        }`}
      >
        {copied ? '✓' : '⧉'}
      </span>
    </button>
  )
}
