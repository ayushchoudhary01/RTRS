# Trade Flow Console

An operations console for the Trade Flow post-trade platform. It submits trades and
then follows them across all seven backend services — risk, AML, choreography,
ledger, settlement and reconciliation — so the pipeline can be observed rather than
read out of seven sets of log lines.

React 19, TypeScript, Vite, Tailwind v4, TanStack Query, React Router.

## Running it

The backend services must already be up; this is a read-mostly view over them.

```bash
npm install
npm run dev
```

Opens on <http://localhost:5173>.

## How it reaches the services

Each backend service owns a distinct `/api/v1` path prefix, so the Vite dev server
fans requests out by prefix with no rewriting:

| Path prefix | Service | Port |
|---|---|---|
| `/api/v1/trades` | trade-ingestion | 8086 |
| `/api/v1/approvals` | trade-processor | 8087 |
| `/api/v1/risk` | risk-engine | 8083 |
| `/api/v1/aml` | aml-engine | 8084 |
| `/api/v1/ledger`, `/api/v1/admin/ledger` | ledger | 8085 |
| `/api/v1/settlement` | settlement | 8089 |
| `/api/v1/reconciliation` | reconciliation | 8088 |

The browser only ever talks to `localhost:5173`, so **no service needs CORS
configuration**. The map lives in `vite.config.ts`.

## Layout

```
src/
├── api/
│   ├── types.ts     response types, mirroring the services' DTOs exactly
│   ├── client.ts    fetch wrapper, ApiError
│   └── hooks.ts     TanStack Query hooks, one per endpoint
├── components/      AppShell, StatusPill, Panel and friends
└── pages/           one per route
```

## Two conventions worth knowing

**Monetary values stay strings.** `BigDecimal` arrives as a string over JSON and is
never parsed to `number`, which would lose precision. Format at render time.

**404 means "not yet", not "failed".** A trade still in flight genuinely has no
ledger entry, settlement instruction or verdict, so those hooks resolve a 404 to
`null` and stop retrying. That is what lets the timeline show a stage as pending
instead of broken.

## Status

Foundation complete — proxy, API layer, query hooks, design system and routing all
work. The four pages are placeholders; every hook they need already exists in
`src/api/hooks.ts`.
