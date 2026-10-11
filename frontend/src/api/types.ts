// Mirrors the response DTOs the Trade Flow services actually return
// BigDecimal and Instant both arrive as strings over JSON, so they stay strings
// here — parsing them to number would lose precision on monetary values

export type TradeType = 'BUY' | 'SELL' | 'SHORT' | 'COVER'

export type TradeStatus =
  | 'PENDING'
  | 'VALIDATED'
  | 'RISK_APPROVED'
  | 'AML_CLEARED'
  | 'EXECUTED'
  | 'SETTLING'
  | 'SETTLED'
  | 'FAILED'

/** trade-ingestion-service :8086 */
export interface Trade {
  tradeId: string
  clientOrderRef: string
  accountId: string
  instrumentId: string
  tradeType: TradeType
  quantity: string
  limitPrice: string
  currency: string
  status: TradeStatus
  market: string
  createdAt: string
}

export interface TradeRequest {
  clientOrderRef: string
  accountId: string
  instrumentId: string
  tradeType: TradeType
  quantity: string
  limitPrice: string
  currency: string
  market: string
}

/** trade-processor-service :8087 — the choreography state */
export interface ApprovalState {
  tradeId: string
  instrumentId: string
  accountId: string
  riskCleared: boolean
  amlCleared: boolean
  riskClearedAt: string | null
  amlClearedAt: string | null
  status: 'PENDING' | 'RISK_CLEARED' | 'AML_CLEARED' | 'APPROVED' | 'REJECTED' | 'TIMEOUT'
  rejectionReason: string | null
  quantity: string
  limitPrice: string
  currency: string
  createdAt: string
  updatedAt: string
}

/** risk-engine-service :8083 */
export interface RiskEvaluation {
  tradeId: string
  instrumentId: string
  accountId: string
  quantity: string
  limitPrice: string
  notionalUsd: string
  outcome: 'APPROVED' | 'BREACHED'
  breachedRule: string | null
  breachReason: string | null
  evaluatedAt: string
}

/** aml-engine-service :8084 */
export interface AmlEvaluation {
  tradeId: string
  instrumentId: string
  accountId: string
  notionalUsd: string
  outcome: 'CLEARED' | 'FLAGGED'
  flaggedRule: string | null
  flaggedReason: string | null
  riskScore: number
  requiresManualReview: boolean
  evaluatedAt: string
}

/** ledger-service :8085 */
export interface AccountBalance {
  accountId: string
  balance: string
  currency: string
  entryCount: number
  lastEntryHash: string | null
  lastEntryAt: string | null
  updatedAt: string
}

export interface LedgerEntry {
  entryId: string
  journalId: string
  accountId: string
  entryType: 'DEBIT' | 'CREDIT'
  amount: string
  currency: string
  balanceAfter: string
  entryHash: string
  prevHash: string
  sequenceNum: number
  createdAt: string
}

/** settlement-service :8089 */
export type SettlementStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'CLEARING'
  | 'SETTLED'
  | 'FAILED'
  | 'RETRYING'
  | 'ESCALATED'

export interface SettlementInstruction {
  id: string
  tradeId: string
  accountId: string
  instrumentId: string
  tradeType: string
  quantity: string
  unitPrice: string
  currency: string
  settlementDate: string
  status: SettlementStatus
  retryCount: number
  nettedObligationId: string | null
  settlementRunId: string | null
  failureReason: string | null
  createdAt: string
  updatedAt: string
}

/** reconciliation-service :8088 */
export type BreakType =
  | 'MISSING_IN_LEDGER'
  | 'MISSING_IN_PROCESSOR'
  | 'MISSING_DEBIT'
  | 'MISSING_CREDIT'
  | 'UNBALANCED_DOUBLE_ENTRY'
  | 'DUPLICATE_LEDGER_ENTRY'
  | 'AMOUNT_MISMATCH'
  | 'CURRENCY_MISMATCH'

export interface ReconciliationBreak {
  id: string
  tradeId: string
  breakType: BreakType
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM'
  expectedValue: string | null
  actualValue: string | null
  status: 'OPEN' | 'RESOLVED'
  detectedAt: string
  resolvedAt: string | null
}

export interface ReconciliationRun {
  id: string
  startedAt: string
  completedAt: string | null
  tradesChecked: number
  breaksFound: number
  breaksResolved: number
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  durationMs: number | null
  failureReason: string | null
}

/** Spring Data Page, as returned by the paginated reconciliation and settlement endpoints */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

/** rtrs-common ApiResponse — used by the submit endpoint and by every error body */
export interface ApiResponse<T> {
  success: boolean
  message: string | null
  data: T | null
  errorCode: string | null
  timestamp: string
}
