import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, api } from './client'
import type {
  AccountBalance,
  AmlEvaluation,
  ApiResponse,
  ApprovalState,
  LedgerEntry,
  Page,
  ReconciliationBreak,
  ReconciliationRun,
  RiskEvaluation,
  SettlementInstruction,
  Trade,
  TradeRequest,
} from './types'

export const queryKeys = {
  trade: (id: string) => ['trade', id] as const,
  approval: (id: string) => ['approval', id] as const,
  riskEvaluation: (id: string) => ['risk', id] as const,
  amlEvaluation: (id: string) => ['aml', id] as const,
  ledgerEntriesByTrade: (id: string) => ['ledger', 'trade', id] as const,
  ledgerEntriesByAccount: (id: string) => ['ledger', 'account', id] as const,
  accountBalance: (id: string) => ['ledger', 'balance', id] as const,
  settlementInstruction: (id: string) => ['settlement', id] as const,
  breaks: (page: number) => ['breaks', page] as const,
  reconciliationRuns: (page: number) => ['recon-runs', page] as const,
}

/** A trade in flight has no ledger entry, no settlement instruction and no
 *  verdicts yet, so 404 means "not yet", not "broken" — swallow it to null and
 *  stop retrying, so the timeline can show each stage as pending */
function nullOn404(err: unknown): never | null {
  if (err instanceof ApiError && err.isNotFound) return null
  throw err
}

const retryUnlessNotFound = (count: number, err: unknown) =>
  !(err instanceof ApiError && err.isNotFound) && count < 2

/** How often the timeline polls while a trade is still moving — the backend's
 *  slowest hop is the 30s choreography timeout, so 2s is responsive without
 *  hammering seven services */
const LIVE_POLL_MS = 2000

interface LiveOptions {
  /** Keep polling while the trade has not reached a terminal state */
  live?: boolean
}

export function useTrade(tradeId: string | undefined, { live }: LiveOptions = {}) {
  return useQuery({
    queryKey: queryKeys.trade(tradeId ?? ''),
    queryFn: () => api.get<Trade>(`/api/v1/trades/${tradeId}`),
    enabled: Boolean(tradeId),
    refetchInterval: live ? LIVE_POLL_MS : false,
    retry: retryUnlessNotFound,
  })
}

export function useApprovalState(tradeId: string | undefined, { live }: LiveOptions = {}) {
  return useQuery({
    queryKey: queryKeys.approval(tradeId ?? ''),
    queryFn: () =>
      api.get<ApprovalState>(`/api/v1/approvals/${tradeId}`).catch(nullOn404),
    enabled: Boolean(tradeId),
    refetchInterval: live ? LIVE_POLL_MS : false,
    retry: retryUnlessNotFound,
  })
}

export function useRiskEvaluation(tradeId: string | undefined, { live }: LiveOptions = {}) {
  return useQuery({
    queryKey: queryKeys.riskEvaluation(tradeId ?? ''),
    queryFn: () =>
      api.get<RiskEvaluation>(`/api/v1/risk/evaluations/${tradeId}`).catch(nullOn404),
    enabled: Boolean(tradeId),
    refetchInterval: live ? LIVE_POLL_MS : false,
    retry: retryUnlessNotFound,
  })
}

export function useAmlEvaluation(tradeId: string | undefined, { live }: LiveOptions = {}) {
  return useQuery({
    queryKey: queryKeys.amlEvaluation(tradeId ?? ''),
    queryFn: () =>
      api.get<AmlEvaluation>(`/api/v1/aml/evaluations/${tradeId}`).catch(nullOn404),
    enabled: Boolean(tradeId),
    refetchInterval: live ? LIVE_POLL_MS : false,
    retry: retryUnlessNotFound,
  })
}

export function useLedgerEntriesByTrade(tradeId: string | undefined, { live }: LiveOptions = {}) {
  return useQuery({
    queryKey: queryKeys.ledgerEntriesByTrade(tradeId ?? ''),
    queryFn: () =>
      api.get<LedgerEntry[]>(`/api/v1/ledger/trades/${tradeId}/entries`).catch(nullOn404),
    enabled: Boolean(tradeId),
    refetchInterval: live ? LIVE_POLL_MS : false,
    retry: retryUnlessNotFound,
  })
}

export function useSettlementInstruction(tradeId: string | undefined, { live }: LiveOptions = {}) {
  return useQuery({
    queryKey: queryKeys.settlementInstruction(tradeId ?? ''),
    queryFn: () =>
      api
        .get<SettlementInstruction>(`/api/v1/settlement/instructions/${tradeId}`)
        .catch(nullOn404),
    enabled: Boolean(tradeId),
    refetchInterval: live ? LIVE_POLL_MS : false,
    retry: retryUnlessNotFound,
  })
}

export function useAccountBalance(accountId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.accountBalance(accountId ?? ''),
    queryFn: () =>
      api.get<AccountBalance>(`/api/v1/ledger/accounts/${accountId}/balance`).catch(nullOn404),
    enabled: Boolean(accountId),
    retry: retryUnlessNotFound,
  })
}

export function useLedgerEntriesByAccount(accountId: string | undefined) {
  return useQuery({
    queryKey: queryKeys.ledgerEntriesByAccount(accountId ?? ''),
    queryFn: () =>
      api.get<LedgerEntry[]>(`/api/v1/ledger/accounts/${accountId}/entries`).catch(nullOn404),
    enabled: Boolean(accountId),
    retry: retryUnlessNotFound,
  })
}

/** Re-walks the account's whole chain server side and recomputes every hash */
export function useVerifyChainIntegrity() {
  return useMutation({
    mutationFn: (accountId: string) =>
      api.get<Record<string, unknown>>(`/api/v1/ledger/accounts/${accountId}/integrity`),
  })
}

export function useBreaks(page = 0) {
  return useQuery({
    queryKey: queryKeys.breaks(page),
    queryFn: () =>
      api.get<Page<ReconciliationBreak>>(`/api/v1/reconciliation/breaks?page=${page}&size=20`),
    // Breaks self-heal on the next reconciliation pass, so this view refreshes
    // on the same cadence the scheduler runs
    refetchInterval: 15000,
  })
}

export function useReconciliationRuns(page = 0) {
  return useQuery({
    queryKey: queryKeys.reconciliationRuns(page),
    queryFn: () =>
      api.get<Page<ReconciliationRun>>(`/api/v1/reconciliation/runs?page=${page}&size=20`),
    refetchInterval: 15000,
  })
}

export function useSubmitTrade() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: TradeRequest) =>
      api.post<ApiResponse<Trade>>('/api/v1/trades', request),
    onSuccess: (response) => {
      const tradeId = response.data?.tradeId
      if (tradeId) {
        queryClient.invalidateQueries({ queryKey: queryKeys.trade(tradeId) })
      }
    },
  })
}
