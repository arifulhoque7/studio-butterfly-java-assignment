# ADR 0001 — Provider resolution: tenant routing and failover as one ordered chain

Status: Accepted
Date: 2026-07-10

## Context

`SmsMessage` carries a `tenantId` that changed nothing: `SmsChannelService` resolved a single gateway
from the global `provider` property. Part 3 asks for two related things — per-tenant provider selection
(with strict isolation between tenants) and failover to a secondary provider — and the retry work needs
somewhere to live too. The question is how routing, failover, and retry compose without turning the
send path into a tangle, and how the gateways are wired so that a provider *other than the global
default* can actually be resolved at runtime.

A constraint shaped everything: the original auto-configuration created **one** gateway bean, gated on
`@ConditionalOnProperty(havingValue = <global provider>)`. So `List<SmsGateway>` held a single element.
Any routing or failover logic that resolved a different provider would find no supporting gateway and
throw. Whatever routing model I chose, the bean wiring had to change with it.

## Decision

Model routing and failover as a single **ordered provider chain**, resolved per message, and run each
element through the retry policy until one succeeds:

1. **Primary** = the tenant's override (`tenant-providers[tenantId]`) if present and non-blank, else the
   global `provider`.
2. **Then** the configured `failover` list, in order.
3. De-duplicate while preserving order (a provider named twice is tried once).

`SmsChannelService` walks the chain: for each provider it looks up the gateway and calls
`RetryableSender.send(gateway, message)`; on success it stops and records cost; on failure it advances to
the next provider. Retry is *within* a provider (transient failures), failover is *across* providers.

To make the chain resolvable, **all gateway beans are registered**, not just the global provider's. A
gateway whose credentials are absent constructs harmlessly and only fails at send time — where failover
takes over.

Tenant isolation falls out of the model: `tenant-providers` is read-only immutable config, looked up by
key per request. One tenant's entry cannot change another's resolution, and there is no shared mutable
routing state.

## Alternatives rejected

- **A Spring child `ApplicationContext` (or bean profile) per tenant.** True isolation, but it does not
  scale to many tenants, makes hot config changes a context reload, and is far heavier than a map
  lookup. Rejected as over-engineering for what is a routing decision, not a wiring decision.
- **Dynamically creating a gateway per send from tenant config.** Rebuilds a `WebClient` (connection
  pool, auth) on every message — wasteful and a latency/GC problem. Rejected; gateways are long-lived
  singletons and routing only *selects* among them.
- **Keeping `@ConditionalOnProperty` per gateway and resolving beans lazily.** Would keep only one bean,
  so failover and tenant overrides could never resolve a second provider. This is the root cause of the
  feature being inert; registering all gateways is the fix, not a workaround.
- **Separate `TenantRouter` and `FailoverManager` components.** Two abstractions for what is one list.
  Collapsing them into one ordered chain keeps the send path readable and the failure semantics obvious
  (retry-then-advance), and puts tenant primary and global failover on the same footing.

## Consequences

- **Positive.** Tenant routing and failover share one code path and one mental model. Retry composes
  cleanly (per-provider, transient-only). Isolation is structural, not defensive. Adding a provider to a
  tenant or to the failover chain is pure configuration.
- **Negative / trade-offs.**
  - All five gateways are instantiated even if only one is configured. Construction is cheap and side-
    effect-free, but an unconfigured provider that ends up in a failover chain will fail at send; the
    chain absorbs it, at the cost of one wasted attempt and a warn log. Acceptable, and visible.
  - Failover currently advances on *any* non-success after retries, including deterministic errors like
    a provider rejecting the number. For a number valid at E.164 but rejected by one carrier this is
    desirable; for a truly malformed number it wastes attempts — but `PhoneNumberValidator` already
    rejects those before the send path, so the exposure is small. A per-error "worth failing over?"
    predicate is the natural next refinement.
  - Cost is recorded against the provider that actually succeeded, which is correct, but means a
    partially-failed failover still incurs the upstream provider's charge for the failed attempt if that
    provider billed on acceptance. Reconciling that needs the delivery-callback work (Finding 15).
