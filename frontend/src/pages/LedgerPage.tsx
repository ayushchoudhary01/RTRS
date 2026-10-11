import { EmptyState, Panel } from '../components/Panel'

export function LedgerPage() {
  return (
    <Panel title="Ledger">
      <EmptyState
        message="Not built yet"
        hint="Phase 2 — the API layer and hooks this page needs are already in src/api"
      />
    </Panel>
  )
}
