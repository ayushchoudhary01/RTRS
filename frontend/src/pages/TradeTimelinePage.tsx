import { EmptyState, Panel } from '../components/Panel'

export function TradeTimelinePage() {
  return (
    <Panel title="Trade timeline">
      <EmptyState
        message="Not built yet"
        hint="Phase 2 — the API layer and hooks this page needs are already in src/api"
      />
    </Panel>
  )
}
