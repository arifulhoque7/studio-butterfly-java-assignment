# formwork-channel-sms — review and hardening

This repository is my submission for the Studio Butterfly take-home. `main` holds the module
exactly as received (AI-generated). All of my work lands on the `review-and-fixes` branch and is
opened as a pull request, so everything I changed shows up as a reviewable diff against the
original.

- **The review:** [`REVIEW.md`](REVIEW.md) — 16 findings, a top-10, three fixes with proof, and a
  test-suite audit.
- **The decision record:** [`docs/adr/0001-provider-resolution-and-failover.md`](docs/adr/0001-provider-resolution-and-failover.md)
- **AI disclosure:** [`AI-USAGE.md`](AI-USAGE.md)

---

## What I changed

### Part 2 — three fixes, each proven test-first (red → green)

| Finding | Harm | Fix | Commits |
|---|---|---|---|
| 1 — cost pipeline never runs | Money: every SMS unbilled | Wire `SmsCostService` into the send path | `c3d2f1b` |
| 2 — AWS SNS SigV4 broken for spaced messages | Outage: `403 SignatureDoesNotMatch` | RFC 3986 encoder for the canonical query | `05fff5f` (red) → `9ff8fca` (green) |
| 3 — full phone numbers logged | PII/GDPR leak | Mask recipients in every gateway log | `e31df6d` (red) → `07f28fb` (green) |

For findings 2 and 3 the failing test is committed *before* the fix. For finding 1 the test and
fix share a commit; the commit message documents that the assertion (`verify(costService).recordCost(...)`)
was red on the original code because no call existed.

### Part 3 — make it work

- **Tenant-aware provider selection.** `formwork.sms-channel.tenant-providers` maps a `tenantId` to
  a provider; a tenant override routes only that tenant and never affects another. Falls back to the
  global provider. All gateway beans are now registered (previously only the global one was), so a
  different provider can actually be resolved at runtime.
- **Retry + failover.** `RetryableSender` retries only transient failures (network/timeout, HTTP 429,
  HTTP 5xx) with capped exponential backoff and **jitter applied before the cap**, so the cap is always
  honoured. Deterministic failures (4xx, config, validation) are not retried. After a provider's retry
  budget is spent, the configured `failover` chain is tried in order. Cost is recorded only on the
  successful send.
- **Multi-segment cost.** `SegmentCalculator` (GSM-7 160/153, UCS-2 70/67, extension chars cost two
  septets) replaces the hardcoded `segmentCount = 1` in the gateways that do not return a count, so
  long messages are no longer under-billed.
- **One honest integration test.** `TwilioSmsGatewayIntegrationTest` drives the gateway over real HTTP
  against a `MockWebServer` and asserts the actual bytes: method, path, `Authorization` header,
  content-type, and form body. Unlike the existing `*WireMockTest` classes (which mock the WebClient
  chain and assert nothing about the request), it fails if the URL, auth, or encoding regresses.

---

## Build and run

### In the real formwork reactor (how it ships)

The module is a library, built as part of the private `formwork` reactor (parent pom +
`formwork-base-tenant`). There it builds and tests with:

```bash
mvn -pl formwork-channel-sms test
```

It is consumed by a Spring Boot app via auto-configuration; enabling it needs
`formwork.sms-channel.provider` plus that provider's credentials. Example configuration:

```yaml
formwork:
  sms-channel:
    provider: TWILIO
    failover: [VONAGE, AWS_SNS]
    tenant-providers:
      "11111111-1111-1111-1111-111111111111": AWS_SNS
    retry:
      max-attempts: 3
      backoff: 1s
      max-backoff: 30s
    twilio:
      account-sid: ${TWILIO_SID}
      auth-token: ${TWILIO_TOKEN}
      from-number: "+15551234567"
```

### Verifying this repo on its own (CI)

`formwork-base-tenant` and the parent pom are private, so a public checkout cannot build the module
directly. The [`verification-harness`](verification-harness/) stubs the one external class the reviewed
logic touches (`TenantScopedEntity`) and builds the module's tenant-independent packages against it.
[`ci/copy-sources.sh`](ci/copy-sources.sh) copies the sources in at build time (no duplicated copy is
committed). To reproduce CI locally:

```bash
bash ci/copy-sources.sh
mvn -f verification-harness/pom.xml verify
```

This runs the whole test suite (180 tests) and enforces a JaCoCo line-coverage gate of 70% on the
logic packages. The [CI workflow](.github/workflows/ci.yml) does the same on every push and PR and
fails the build if coverage drops below the gate.

> Note on the coverage threshold: 70%, not 90%+, is deliberate. The assignment discounts "100%
> coverage of trivial getters," so the gate excludes POJO records, Spring wiring (`config`), and
> `package-info`, and holds the *logic* — send path, cost pipeline, SigV4 signing, retry/backoff,
> segment counting — honestly covered. A higher number would reward padding the trivial classes.

> Note on JDK: CI runs on JDK 21 (the module targets 21; `List.getFirst()` and friends are 21 APIs).
> On JDK 24+ the bundled Mockito needs `-Dnet.bytebuddy.experimental=true`, which the harness sets.

---

## What I'd do next with another week

In rough priority order (these are the remaining findings from `REVIEW.md`):

1. **HTTP timeouts on every gateway (Finding 6).** No `WebClient` today has a connect/response
   timeout, so a hung provider blocks the calling thread and `sendBulk` stalls entirely. This is the
   most important thing I did *not* finish; retry/backoff is only fully effective once timeouts turn a
   hang into a retryable failure.
2. **Idempotency (Finding 12).** Thread `referenceId` to provider idempotency keys and add a unique
   constraint on `(tenant_id, message_id)`, so retry/failover and client resends cannot double-send or
   double-charge.
3. **Country-code correctness (Finding 10).** Replace the hand-rolled prefix table (which returns
   non-ISO junk like `"813"` for Japan) with libphonenumber, and fail closed on unknown countries
   rather than silently defaulting the rate.
4. **Delivery callbacks (Finding 15).** `handleDeliveryCallback` is a no-op; wire per-provider status
   updates and reconcile cost against final delivery.
5. **AWS credentials via the default provider chain / STS (Finding 16).** Support IAM roles, rotation,
   and per-tenant account separation instead of static env-var keys.

---

## What I deliberately did not do (and why)

- **No rewrite.** The brief penalises rewrites; I made the smallest change that fixes each defect and
  left working code alone.
- **`sendBulk` left sequential.** Making it bounded-parallel matters, but only after timeouts exist
  (Finding 6); parallelising unbounded blocking calls trades one failure mode for another. Scoped, not
  built.
- **Per-tenant rate isolation (Finding 13) not implemented.** The rate registry is global and mutable;
  I flagged it but did not add a tenant dimension, because no code currently calls `setRate` per tenant,
  so it is latent rather than live. Documented instead of gold-plated.
- **BudgetSMS credential-in-URL (Finding 14) not changed.** The provider's API is GET-with-query by
  design; the real mitigation is log-layer redaction, which belongs in the platform's logging config,
  not this module.
- **The existing `*WireMockTest` files were left in place** (I added a real integration test alongside)
  rather than deleted, to keep the diff focused on additions; the review recommends replacing them.

I optimised for two Part 3 items done properly plus the two smaller ones, over four done carelessly —
though in practice all four landed with tests. The honest cut is the timeout work in item 1 above.

---

## Repository layout

```
formwork-channel-sms/     the module (source of truth)
verification-harness/     standalone build so this repo is CI-verifiable without the private reactor
ci/copy-sources.sh        copies module sources into the harness at build time
.github/workflows/ci.yml  build + test + coverage gate
REVIEW.md                 Part 1 review + Part 2 test evidence
docs/adr/0001-*.md        the most consequential design decision
AI-USAGE.md               how I used AI, and where it was wrong
```
