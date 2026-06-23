# 10 — Shared Libraries

Three Maven modules, built once and installed to the local `.m2` repository
before any service can compile against them (see
`docs/troubleshooting/004-fresh-clone-shared-libraries-not-found.md` for the
fresh-clone setup this requires). Every service depends on all three.

## rtrs-common

General-purpose building blocks with no domain-specific Kafka or DB logic.

**DTOs** — `TradeDto`, `AccountDto`. Plain carriers, not entities.

**Enums** — `TradeStatus`, `ErrorCode`. `TradeType` here is worth noting
specifically: it includes `BUY`, `SELL`, `SHORT`, and `COVER` — more than the
two values (`BUY`/`SELL`) that any service currently actually uses. The
extra values exist for short-selling support that hasn't been built yet, not
dead code from something removed.

**Exception hierarchy** — `DomainException` as the common parent,
`TradeNotFoundException` and `IdempotencyException` as concrete subtypes.
`GlobalExceptionHandler` here is the *shared* exception-handling logic;
individual services like reconciliation-service and settlement-service
additionally define their own local `GlobalExceptionHandler` for
service-specific exceptions (like their own `ResourceNotFoundException`)
rather than extending this one — the shared handler covers genuinely
cross-cutting concerns, not every possible exception type in the system.

**`ApiResponse`** — a builder-pattern response envelope used by
trade-ingestion's REST layer to wrap success/error responses consistently.

## rtrs-events

Ten Avro schema-generated Java classes, defining every event contract in the
system — including some that are designed but not currently wired into
production code paths. This module is the single source of truth for what
an event *can* look like, independent of whether a given service actually
uses the Avro form on the wire.

**Actually used as real Avro on the wire** (risk-engine, aml-engine publish
these; trade-processor consumes them):
- `RiskApprovedEvent`, `RiskBreachedEvent`
- `AmlClearedEvent`, `AmlFlaggedEvent`

**Schema exists, but the wire format actually used is plain JSON via the
outbox pattern** (see `docs/system-design/00-overview.md` for why):
- `TradeSubmittedEvent`, `TradeExecutedEvent`, `TradeRejectedEvent`
- `LedgerEntryCreatedEvent`

These four schemas were generated up front, alongside the ones that are
genuinely used, and they remain accurate descriptions of the event shape —
`TradeExecutedEvent`'s Avro schema even includes a `settlementDate` field
that correctly anticipated settlement-service's eventual existence. They're
not dead schemas so much as schemas whose enforcement happens informally
(by convention across the services that read/write the JSON payload) rather
than at the serialization layer.

**`EventMetadata`** — embedded in every event (Avro or not, conceptually):
`eventId`, `eventVersion`, `eventType`, `correlationId`, `causationId`,
`source`, `publishedAt`, and an optional `traceId` field for distributed
tracing — present in the schema and ready to be populated the moment
tracing is actually wired up (see `docs/future/012-distributed-tracing.md`).

**Enums** — `BreachType` (risk), `EntryType` (DEBIT/CREDIT, ledger),
`ExecutionStatus`, `RejectionReason`. These are the enums embedded *inside*
Avro events, distinct from the plain Java enums each service defines for its
own internal entity state (e.g. settlement-service's own
`SettlementStatus`, which has no Avro equivalent because it's never
serialized to Kafka directly).

**Schema versioning policy** — BACKWARD compatibility enforced via
Confluent Schema Registry; new fields must have defaults, existing fields
are never removed or renamed, breaking changes get a new topic
(`trade.submitted.v2`) rather than a schema mutation. Formal reasoning in
`docs/architecture-decision-records/007-avro-schema-versioning.md`.

## rtrs-security

JWT validation and role-based access control — genuinely well-built, but
**not currently wired into any service's actual request pipeline**.

**`JwtValidator`** — validates HS256 signature, issuer, audience, and token
type claims; throws on any mismatch. Fully implemented, fully correct.

**`AuthenticatedUser`** — the principal object a valid token resolves to,
carrying user ID and roles.

**`RtrsRole`** — the role enum (e.g. roles for risk managers, compliance
officers, administrators).

**`@RequiresRole`** + **`RoleEnforcementAspect`** — an AOP aspect that
intercepts any method annotated `@RequiresRole(RtrsRole.X)` and checks the
current request's authenticated user against the required role, throwing
if it doesn't match. The implementation reads the authenticated user from
`request.getAttribute("authenticatedUser")`.

**The gap**: nothing in any service currently sets that request attribute.
There's no Spring Security filter or interceptor anywhere in the codebase
that takes an incoming JWT, validates it via `JwtValidator`, and populates
`authenticatedUser` on the request before a controller method runs. The
validator and the enforcement aspect are both real, correct, and ready —
they're just not connected to anything yet, because building the actual
filter (and the auth-service that would issue tokens in the first place)
was deliberately deferred. Every `@RequiresRole`-annotated method in the
codebase today would fail at runtime, not because the security logic is
wrong, but because nothing upstream of it has run yet.

This is consistent with the system-wide pattern of `@RequiresRole`
annotations being present on controller methods across services but
stripped of enforcement weight until the auth piece exists — documented as
deliberately deferred, not forgotten.