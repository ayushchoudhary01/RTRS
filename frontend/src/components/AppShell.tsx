import { Link, Outlet, useLocation } from 'react-router-dom'
import { useLastViewed } from '../lib/lastViewed'

export function AppShell() {
  const { pathname } = useLocation()
  const { tradeId, accountId } = useLastViewed()

  // Each tab carries the id you last had open, so switching screens and coming
  // back lands on the same trade or account instead of an empty form
  const nav = [
    { base: '/submit', to: '/submit', label: 'Submit' },
    { base: '/trades', to: tradeId ? `/trades/${tradeId}` : '/trades', label: 'Trade timeline' },
    { base: '/ledger', to: accountId ? `/ledger/${accountId}` : '/ledger', label: 'Ledger' },
    { base: '/breaks', to: '/breaks', label: 'Reconciliation' },
  ]

  // Matched on the base path, so /trades/<id> still highlights Trade timeline
  const isActive = (base: string) => pathname === base || pathname.startsWith(`${base}/`)

  return (
    <div className="min-h-screen">
      <header className="border-b border-surface-border bg-surface-raised">
        <div className="mx-auto flex max-w-7xl flex-col gap-3 px-6 py-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-baseline gap-3">
            <span className="text-sm font-semibold tracking-tight">Trade Flow</span>
            <span className="text-xs text-content-muted">Post-trade operations console</span>
          </div>

          <nav className="flex gap-1 overflow-x-auto">
            {nav.map(({ base, to, label }) => (
              <Link
                key={base}
                to={to}
                aria-current={isActive(base) ? 'page' : undefined}
                className={`whitespace-nowrap rounded-md px-3 py-1.5 text-sm transition-colors ${
                  isActive(base)
                    ? 'bg-surface-overlay text-content-primary'
                    : 'text-content-secondary hover:bg-surface-overlay/60 hover:text-content-primary'
                }`}
              >
                {label}
              </Link>
            ))}
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-6 py-6">
        <Outlet />
      </main>
    </div>
  )
}
