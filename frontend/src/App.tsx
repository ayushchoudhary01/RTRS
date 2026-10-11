import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { BreaksPage } from './pages/BreaksPage'
import { LedgerPage } from './pages/LedgerPage'
import { SubmitTradePage } from './pages/SubmitTradePage'
import { TradeTimelinePage } from './pages/TradeTimelinePage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/submit" replace />} />
        <Route path="submit" element={<SubmitTradePage />} />
        {/* Timeline is reachable with or without a trade id, so a submit can
            redirect straight into the trade it just created */}
        <Route path="trades" element={<TradeTimelinePage />} />
        <Route path="trades/:tradeId" element={<TradeTimelinePage />} />
        <Route path="ledger" element={<LedgerPage />} />
        <Route path="ledger/:accountId" element={<LedgerPage />} />
        <Route path="breaks" element={<BreaksPage />} />
        <Route path="*" element={<Navigate to="/submit" replace />} />
      </Route>
    </Routes>
  )
}
