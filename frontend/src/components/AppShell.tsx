import { NavLink, Outlet } from 'react-router-dom'

const NAV = [
  { to: '/submit', label: 'Submit' },
  { to: '/trades', label: 'Trade timeline' },
  { to: '/ledger', label: 'Ledger' },
  { to: '/breaks', label: 'Reconciliation' },
]

export function AppShell() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-surface-border bg-surface-raised">
        <div className="mx-auto flex max-w-7xl flex-col gap-3 px-6 py-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-baseline gap-3">
            <span className="text-sm font-semibold tracking-tight">Trade Flow</span>
            <span className="text-xs text-content-muted">Post-trade operations console</span>
          </div>

          <nav className="flex gap-1 overflow-x-auto">
            {NAV.map(({ to, label }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `whitespace-nowrap rounded-md px-3 py-1.5 text-sm transition-colors ${
                    isActive
                      ? 'bg-surface-overlay text-content-primary'
                      : 'text-content-secondary hover:bg-surface-overlay/60 hover:text-content-primary'
                  }`
                }
              >
                {label}
              </NavLink>
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
