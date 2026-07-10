# REVIEW.md — formwork-channel-sms

Reviewer: solo backend owner.
Scope: entire `formwork-channel-sms` module, source and tests, read line by line.
Method: traced the send path end to end (`SmsChannelService.sendSms` → gateway → cost pipeline), then audited each provider, the cost recording code, configuration/wiring, persistence, and every test against the question the assignment asks: *if I broke the production code this test covers, would the test fail?*

The headline: the module compiles and its tests are green, but the cost pipeline never runs, the AWS SNS gateway cannot authenticate a normal message, full phone numbers are written to logs, and the provider tests assert nothing about what actually goes over the wire. These are not style issues; they lose money, leak personal data, and fail silently in production.

---

## 1. Executive Summary

**Production readiness: not ready. Do not ship.**

Four defects are individually sufficient to block a release:

1. **The cost pipeline is dead code.** `SmsCostService`, `SmsCostRepository`, `ProviderRateRegistry`, the `sms_cost_record` table, and its Flyway migration all exist and are tested in isolation — but `SmsChannelService` never calls `recordCost`. Every SMS the platform sends is unbilled. There is no per-tenant cost data, so tenants cannot be charged and spend cannot be monitored. This is the money bug.

2. **AWS SNS is broken for essentially every real message.** The SigV4 canonical request is built with `URLEncoder.encode` (form encoding), which renders a space as `+` and leaves `*` unescaped. AWS SigV4 requires RFC 3986 encoding (space → `%20`). AWS re-canonicalizes the request server-side with RFC 3986 and gets a different string to sign, so the signature never matches. Any message body containing a space — i.e. almost all of them — returns `403 SignatureDoesNotMatch`. For a tenant on AWS SNS this is a total outage.

3. **Personal data is leaked to logs.** Every gateway logs the full recipient MSISDN at INFO on the success path, and error paths log the provider's raw response body (which echoes the number). This is an `eu-central-1` platform; phone numbers are personal data under GDPR. Meanwhile the cost service painstakingly masks the same number before persisting it — the discipline exists, it just was not applied where it matters.

4. **The provider test suite proves nothing about the wire.** The five `*WireMockTest` classes use no WireMock. They mock the entire `WebClient` fluent chain with Mockito and assert on the value the mock was told to return. Rename the Twilio form field from `To` to `Recipient`, drop the `Authorization` header, or point the request at the wrong URL, and every one of these tests still passes. They give false confidence, and that false confidence is precisely how bugs 1–3 shipped.

Below these sit missing retry/failover (the config exists, nothing uses it), inoperative tenant-aware provider selection, hardcoded segment counts that under-bill multi-part messages, and the absence of any HTTP timeout (one hung provider blocks the calling thread forever, and `sendBulk` is sequential, so it blocks the whole batch).

The engineering pattern across the module is consistent and worth naming: **each unit looks correct in isolation and is tested in isolation, but the units were never wired together, and no test exercises them together.** The seams are where every serious bug lives.

---

## 2. Top 10 Most Important Bugs (ordered by severity)

| # | Severity | Bug | Harm |
|---|----------|-----|------|
| 1 | Critical | Cost recording is never invoked from the send path | Money — every SMS is unbilled; no cost data exists |
| 2 | Critical | AWS SNS SigV4 uses form-encoding, not RFC 3986 | Requests failing — every spaced message → 403, provider outage |
| 3 | Critical | Full recipient phone numbers logged in plaintext | Data leaked — GDPR-relevant PII in logs |
| 4 | High | Provider `*WireMockTest`s mock the whole WebClient chain; assert nothing on the wire | False confidence — wire-format regressions ship green |
| 5 | High | Segment count hardcoded to 1 (AWS SNS, MessageBird, BudgetSMS) | Money — multi-segment messages under-billed |
| 6 | High | No HTTP timeout on any blocking gateway call | Reliability — a hung provider blocks threads; `sendBulk` stalls entirely |
| 7 | High | Retry/failover unimplemented despite `RetryProperties` | Reliability — one transient 5xx = permanent send failure |
| 8 | High | Tenant-aware provider selection ignores `tenantId` | Correctness/isolation — all tenants forced onto the global provider |
| 9 | Medium | AWS SNS gateway has zero behavioural test coverage | The most complex, most broken gateway is the least tested |
| 10 | Medium | `extractCountryCode` returns non-ISO garbage for unlisted countries; `00` prefixes only partly handled | Money — mis-rated cost and junk `country_code` data |

Two or more of these are production-incident defects (money lost: #1, #5; requests failing: #2; data leaked: #3), satisfying the assignment's bar.

---

## 3. Findings

Each finding uses the requested format.

---

### Finding 1

**Severity:** Critical
**Title:** Cost recording never happens — `SmsCostService` is orphaned; the send path never calls it
**Category:** Correctness / Billing / Dead wiring
**File:** `src/main/java/one/formwork/channel/sms/api/SmsChannelService.java`
**Line(s):** 15–24 (constructor and `sendSms`); the whole `cost` package is the orphan
**Root Cause:** `SmsChannelService` depends only on `List<SmsGateway>` and `SmsChannelProperties`. It never injects or calls `SmsCostService`. `sendSms` validates, resolves a gateway, calls `gateway.send`, and returns the result. Nothing records cost.
**Mechanism:** `recordCost` is `@Transactional` and correct when called directly (its unit test drives it in isolation), but there is no caller anywhere in the module. `SmsCostService` is instantiated as a Spring bean and then never referenced. The Flyway migration creates `sms_cost_record`; that table stays empty forever.
**Production Impact:** No per-tenant cost is ever written. Tenants cannot be billed. `getMonthlyCost`, `getCostBreakdown`, and `getSmsCount` all return empty/zero for every tenant regardless of traffic. Spend and budget alerting are impossible. This is the single most expensive defect in the module.
**Likelihood:** Certain — occurs on 100% of sends.
**How to Reproduce:** Call `sendSms` with a valid message and a stubbed successful gateway. Query `SmsCostRepository`. Zero rows. `verify(costService, never()).recordCost(...)` passes against the current code.
**Recommended Fix:** Inject `SmsCostService` into `SmsChannelService`. After a successful `gateway.send`, call `recordCost(message.tenantId(), message.to(), result)` inside the request's tenant context and transaction. Record only on success (the service already guards `isSuccess()`). Add a unit test that verifies the interaction, and an integration test that asserts a row lands in `sms_cost_record` with the right tenant, provider, segments, and total.
**Suggested Patch:**
```java
public SmsResult sendSms(SmsMessage message) {
    PhoneNumberValidator.validate(message.to());
    SmsGateway gateway = resolveGateway(message);
    SmsResult result = gateway.send(message);
    if (result.isSuccess()) {
        costService.recordCost(message.tenantId(), message.to(), result);
    }
    return result;
}
```
(with `SmsCostService costService` added to the constructor.)
**Confidence:** High

---

### Finding 2

**Severity:** Critical
**Title:** AWS SNS SigV4 signature is invalid for any message containing a space
**Category:** Security / Authentication / Provider integration
**File:** `src/main/java/one/formwork/channel/sms/provider/AwsSnsSmsGateway.java`
**Line(s):** 120–122 (`encode`), used at 57–60 and 90; canonical request built at 74–82
**Root Cause:** `encode` delegates to `URLEncoder.encode(value, UTF_8)`, which implements `application/x-www-form-urlencoded`: space becomes `+`, and `*`, `~` etc. are handled per HTML form rules, not per RFC 3986. AWS Signature Version 4 mandates RFC 3986 percent-encoding for the canonical query string — space must be `%20`, `*` must be `%2A`, `~` must remain unescaped.
**Mechanism:** The client signs a canonical request whose query string encodes the message with `+` for spaces. AWS receives the request, re-derives the canonical query string using RFC 3986 (`%20`), computes the signature over *that*, and compares. The two differ, so AWS returns `403 SignatureDoesNotMatch`. Because the same wrong `encode` is used for both signing and the outgoing URI, the bug is invisible to a reader who only checks that "the signed string matches the sent string" — the mismatch is created on AWS's side.
**Production Impact:** Every SMS whose body contains a space (practically all of them) fails with 403. A tenant configured for `AWS_SNS` has a complete outage. AWS SNS is also the cheapest configured provider (0.046 EUR/DE vs Twilio 0.075), so it is a provider a cost-conscious operator would actually select.
**Likelihood:** Near-certain for real traffic; a single-word test message would slip through, which is exactly why the existing tests miss it.
**How to Reproduce:** Point the gateway at a local stub that captures the request, set the AWS env vars, and send body `"Hello world"`. The query string contains `Message=Hello+world`. Feed that canonical request through AWS's documented SigV4 example and the signatures diverge; against the real endpoint it returns `SignatureDoesNotMatch`.
**Recommended Fix:** Replace `encode` with an RFC 3986 encoder: percent-encode everything except `A–Z a–z 0–9 - _ . ~`, and specifically emit `%20` for space. Apply it to both the canonical query string and the request URI.
**Suggested Patch:**
```java
private static String encode(String value) {
    // RFC 3986 unreserved chars stay literal; everything else percent-encoded.
    StringBuilder sb = new StringBuilder();
    for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
        char c = (char) (b & 0xFF);
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
            sb.append(c);
        } else {
            sb.append('%').append(String.format("%02X", b & 0xFF));
        }
    }
    return sb.toString();
}
```
**Confidence:** High

---

### Finding 3

**Severity:** Critical
**Title:** Recipient phone numbers (PII) written to logs in plaintext by every gateway
**Category:** Security / Privacy / GDPR
**File:** all five gateways
**Line(s):** `TwilioSmsGateway.java:54`, `VonageSmsGateway.java:55`, `MessageBirdSmsGateway.java:47`, `BudgetSmsGateway.java:39`, `AwsSnsSmsGateway.java:99`; plus error paths that log `e.getResponseBodyAsString()` (e.g. `AwsSnsSmsGateway.java:102–103`)
**Root Cause:** Success logs use `to={}` with `message.to()`, the full E.164 number. Error logs dump the provider's raw response body, which routinely echoes the recipient number back.
**Mechanism:** These INFO/ERROR lines land in centralised logging (stdout → log aggregation). A phone number is personal data under GDPR; storing it unmasked in logs, with the retention and access controls typical of a log platform, is an unlawful disclosure. The irony: `SmsCostService.maskRecipient` masks the same number to `+491***90` before persistence, so the codebase already knows this data must be masked — the gateways just do not do it.
**Production Impact:** Every sent message deposits a customer phone number in logs. A log export, a support engineer with log access, or a breach turns into a personal-data incident and a reportable GDPR event. This is the "leak personal data" harm the brief names explicitly.
**Likelihood:** Certain — every send logs.
**How to Reproduce:** Attach a Logback `ListAppender` to `TwilioSmsGateway`'s logger, send to `+4915112345678`, assert the captured output does not contain `4915112345678`. It fails today.
**Recommended Fix:** Log the masked number (reuse a shared masking helper — extract `maskRecipient` to a small utility used by both the cost service and the gateways), or log only a hashed/last-two-digits form. Never log raw provider response bodies at ERROR; log status plus an internal correlation id, and route full bodies to a debug channel that is off by default.
**Suggested Patch (illustrative, per gateway):**
```java
log.info("Twilio SMS sent: sid={}, to={}", sid, PhoneMasker.mask(message.to()));
```
**Confidence:** High

---

### Finding 4

**Severity:** High
**Title:** `*WireMockTest` classes use no WireMock and assert nothing about the request sent
**Category:** Test quality / False confidence
**File:** `src/test/.../provider/TwilioSmsGatewayWireMockTest.java`, `VonageSmsGatewayWireMockTest.java`, `MessageBirdSmsGatewayWireMockTest.java`, `BudgetSmsGatewayWireMockTest.java` (and by omission, no AWS equivalent)
**Line(s):** e.g. `TwilioSmsGatewayWireMockTest.java:44–56`
**Root Cause:** The tests mock the entire `WebClient` fluent chain (`post().uri().contentType().bodyValue().retrieve().bodyToMono()`) with Mockito and stub `bodyToMono` to return a canned `Map`. They then assert on fields of that canned map.
**Mechanism:** Because the HTTP client is fully mocked, the test never observes the URL, headers, or body the gateway actually builds. The assertions (`assertEquals("SM123", result.messageId())`) only confirm that the gateway copies a value out of the map the test itself supplied. The request-construction logic — the exact thing that talks to a real provider — is never exercised.
**Production Impact:** No regression protection on wire format. Rename Twilio's `To`/`From`/`Body` fields, drop the Basic auth header, change the URL path, break form encoding, or corrupt the SigV4 signing, and every one of these tests stays green. This is why Findings 2, 3, and 5 could ship. The file names actively mislead: a reader greps for "WireMock" and assumes real HTTP coverage exists.
**Likelihood:** Certain — structural.
**How to Reproduce:** Change `"To=" + encode(message.to())` to `"Recipient=" + encode(message.to())` in `TwilioSmsGateway` and run the suite. It passes. A real provider would reject the request.
**Recommended Fix:** Replace with real HTTP tests against a stub server (WireMock or `MockWebServer`). Point the gateway's `WebClient` at the stub's base URL, send, and assert on the recorded request: method, path, headers (`Authorization`), and body bytes (`To=%2B49...&From=...&Body=...`). At minimum add one such honest test per gateway; prioritise AWS SNS, where the signing logic is both critical and currently untested.
**Suggested Patch:** New test using `okhttp3.mockwebserver.MockWebServer`, e.g. `RecordedRequest req = server.takeRequest(); assertEquals("/Accounts/AC123/Messages.json", req.getPath()); assertTrue(req.getBody().readUtf8().contains("To=%2B4915112345678"));`
**Confidence:** High

---

### Finding 5

**Severity:** High
**Title:** Segment count hardcoded to 1 in AWS SNS, MessageBird, and BudgetSMS — multi-segment messages under-billed
**Category:** Billing / Correctness
**File:** `AwsSnsSmsGateway.java:100`, `MessageBirdSmsGateway.java:48`, `BudgetSmsGateway.java:40`
**Root Cause:** These gateways return `SmsResult.success(messageId, provider, 1)` unconditionally, ignoring the real number of SMS segments. A GSM-7 message over 160 characters (or 70 for UCS-2) splits into multiple segments, each billed separately by the carrier.
**Mechanism:** `SmsCostService.recordCost` computes `totalCost = costPerSegment * max(segmentCount, 1)`. With `segmentCount` pinned at 1, a message the carrier charges as 4 segments is recorded as 1. Cost is under-recorded by up to ~75% on long messages.
**Production Impact:** Systematic under-billing once cost recording is wired (Finding 1). The platform pays the carrier for N segments but records and bills the tenant for 1. Direct, silent margin loss that grows with message length.
**Likelihood:** High for any real content (OTP + branding, appointment reminders, marketing all commonly exceed one segment).
**How to Reproduce:** Send a 200-character body via AWS SNS; the recorded `segment_count` is 1 and `total_cost` equals the single-segment rate.
**Recommended Fix:** Derive segments from the actual payload when the provider does not return a count: compute GSM-7 vs UCS-2 encoding and divide by 160/153 (or 70/67 for UCS-2). Where the provider returns a count in the response (Twilio's `num_segments`; MessageBird exposes recipient/message part data), parse and use it. AWS SNS does not return a segment count synchronously, so compute it locally.
**Suggested Patch:** Introduce `SegmentCalculator.segments(String body)` and pass its result instead of the literal `1`.
**Confidence:** High

---

### Finding 6

**Severity:** High
**Title:** No HTTP timeout on any gateway; a hung provider blocks the caller indefinitely, and `sendBulk` blocks the whole batch
**Category:** Reliability / SRE
**File:** all five gateways (blocking `.block()` calls) and `SmsChannelService.java:26–28` (`sendBulk`)
**Line(s):** e.g. `TwilioSmsGateway.java:40–46`, `AwsSnsSmsGateway.java:89–95`, `BudgetSmsGateway.java:25–35`
**Root Cause:** Each gateway builds a plain `WebClient.builder().build()` with no response timeout and calls `.bodyToMono(...).block()`. There is no `.timeout(...)`, no connect/read timeout on the underlying connector.
**Mechanism:** If a provider accepts the connection but never responds, `.block()` waits forever on the calling thread. `sendBulk` maps `sendSms` sequentially over the list, so a single stalled provider call halts the entire batch and pins the request thread. Under load this exhausts the servlet thread pool — a slow dependency becomes a full-service outage.
**Production Impact:** A degraded (not even failed) provider takes down SMS sending platform-wide. No timeout means no bounded latency and no opportunity for the retry/failover logic (Finding 7) to ever trigger.
**Likelihood:** Medium-High — provider latency spikes and half-open connections are routine.
**How to Reproduce:** Point a gateway at a stub that accepts the socket and never replies. `sendSms` never returns. Wrap several such messages in `sendBulk`; the whole call hangs.
**Recommended Fix:** Configure connect and response timeouts on the `WebClient` connector, and add `.timeout(Duration.ofSeconds(n))` on the Mono. Treat timeout as a retryable failure (Finding 7). Consider making `sendBulk` bounded-concurrent rather than strictly sequential.
**Suggested Patch:**
```java
.bodyToMono(Map.class)
.timeout(Duration.ofSeconds(10))
.block();
```
plus a connector with connect/read timeouts.
**Confidence:** High

---

### Finding 7

**Severity:** High
**Title:** Retry and failover are unimplemented; `RetryProperties` is parsed and tested but wired to nothing
**Category:** Reliability
**File:** `SmsChannelService.java:20–24`; config at `SmsChannelProperties.java:58–62`
**Root Cause:** `RetryProperties` (`maxAttempts=3`, `backoff="5s"`) exists, has getters/setters, and is unit-tested — but `sendSms` calls the gateway exactly once and returns whatever it gets. There is no retry loop, no backoff, and no failover to a secondary provider.
**Mechanism:** A transient failure (HTTP 5xx, timeout, connection reset) is returned to the caller as a permanent `SmsResult.failure`. The configured retry budget is never consulted.
**Production Impact:** Ordinary transient blips cause user-visible SMS loss (missed OTPs, missed reminders). There is no resilience to a single provider's brief degradation, and no way to shift traffic to a healthy provider.
**Likelihood:** High over any real time window.
**How to Reproduce:** Stub a gateway to return a 503 once then succeed. `sendSms` returns failure after the first attempt; it never retries.
**Recommended Fix:** Implement retry with capped exponential backoff **and jitter applied before the cap** (a common mistake is to add jitter after capping, which defeats the cap). Retry only idempotent-safe, transient failures — timeouts, connection resets, HTTP 429/503. Do **not** retry deterministic 4xx (invalid number, auth failure): retrying them wastes time and, without idempotency (Finding 12), risks duplicate sends. Add failover to a configured secondary provider once the primary's retries are exhausted. Parse `backoff` into a `Duration` (it is a raw `String` today).
**Suggested Patch:** A `RetryExecutor` around `gateway.send`, classifying failures via `errorCode`, honouring `properties.getRetry()`, then falling over to a secondary gateway resolved the same way as the primary.
**Confidence:** High

---

### Finding 8

**Severity:** High
**Title:** Tenant-aware provider selection does nothing; `tenantId` never influences routing
**Category:** Multi-tenancy / Correctness
**File:** `SmsChannelService.java:34–40` (`resolveGateway`), called from `sendSms:22`
**Root Cause:** `resolveGateway()` takes no argument and reads only the global `properties.getProvider()`. `message.tenantId()` is carried through the API but never consulted for routing.
**Mechanism:** Every tenant is served by the single globally configured provider. A tenant that should be on a different provider (for price, deliverability, sender-ID rules, or data-jurisdiction reasons) is silently routed to the global default.
**Production Impact:** No per-tenant provider policy. A tenant that negotiated a cheaper provider, or must use a specific in-region provider for regulatory reasons, is routed incorrectly — a cost, deliverability, and potentially compliance problem. This is the exact gap the brief calls out ("`tenantId` currently changes nothing").
**Likelihood:** Certain wherever per-tenant routing is expected.
**How to Reproduce:** Configure the global provider as Twilio, send a message for a tenant meant to be on Vonage; it goes to Twilio.
**Recommended Fix:** Introduce a per-tenant provider resolver (a `Map<UUID, String>` from config or a lookup service) consulted first, falling back to the global default. Resolve strictly from immutable per-tenant configuration so that one tenant's settings can never mutate another's (see Finding 13 on shared mutable state). Pass `message` into `resolveGateway`.
**Suggested Patch:**
```java
private SmsGateway resolveGateway(SmsMessage message) {
    String providerType = tenantProviderConfig.providerFor(message.tenantId())
            .orElse(properties.getProvider());
    return gateways.stream().filter(g -> g.supports(providerType)).findFirst()
            .orElseThrow(() -> new IllegalStateException("No SmsGateway for provider: " + providerType));
}
```
**Confidence:** High

---

### Finding 9

**Severity:** Medium
**Title:** AWS SNS gateway has no behavioural test coverage — the most complex, most broken gateway is the least tested
**Category:** Test quality / Coverage gap
**File:** `AwsSnsSmsGatewayTest.java`, `AwsSnsSmsGatewayExtraTest.java`
**Line(s):** whole files
**Root Cause:** The only AWS tests cover `supports`, `getProviderName`, and the `CONFIG_ERROR` branch when env vars are absent. There is no test for signing, request construction, XML response parsing, or the success path — and, unlike the other four providers, no `*WireMockTest` at all.
**Mechanism:** The SigV4 signing (the module's most intricate and security-sensitive code) executes in zero tests. The `send` success path is never entered because tests short-circuit on missing credentials.
**Production Impact:** Finding 2 (invalid signature) ships completely undetected. Any future change to signing has no safety net.
**Likelihood:** N/A (this is a coverage gap that enables other defects).
**How to Reproduce:** Inspect coverage: `AwsSnsSmsGateway.send` past the credential check is never executed by the suite.
**Recommended Fix:** Add a real HTTP test (`MockWebServer`) with fixed credentials and a fixed clock, asserting the canonical query encoding (`%20` for spaces), the `Authorization` header shape, and correct `MessageId` extraction from a canned SNS XML response. This test doubles as the regression test for Finding 2.
**Suggested Patch:** See Finding 2's test plan in Section 5.
**Confidence:** High

---

### Finding 10

**Severity:** Medium
**Title:** `extractCountryCode` returns non-ISO garbage for unlisted countries and mishandles `00` international prefixes
**Category:** Billing / Data quality
**File:** `src/main/java/one/formwork/channel/sms/cost/SmsCostService.java`
**Line(s):** 98–112 (fallback at 110)
**Root Cause:** Only nine `+`-prefixed countries plus four `00`-prefixed ones are mapped. The fallback `return cleaned.substring(1, Math.min(4, cleaned.length()))` returns raw dialing digits (e.g. `"813"` for Japan `+81…`), not an ISO country code. `00`-prefixed numbers are handled for only DE/AT/CH/GB; `0033` (France) and others fall through to `"XX"`.
**Mechanism:** The returned string feeds both `ProviderRateRegistry.getRate(provider, countryCode)` and the `country_code` column (VARCHAR(5)). Rates are keyed by ISO codes (`DE`, `US`); a `"813"` or `"XX"` key never matches, so every unlisted country silently bills at `DEFAULT_RATE` (0.07 EUR) regardless of the true carrier rate, and the stored country code is meaningless for reporting.
**Production Impact:** Cost mis-rated for any country outside the hardcoded set (which is most of the world), and cost analytics grouped by `country_code` are polluted with junk values. Under- or over-charging depending on the real rate versus 0.07.
**Likelihood:** High for any international traffic.
**How to Reproduce:** `extractCountryCode("+81312345678")` returns `"813"`; `getRate("TWILIO","813")` falls back to `0.07`.
**Recommended Fix:** Use a real E.164 country-code prefix table (or libphonenumber) mapping to ISO codes, and normalise `00` prefixes to `+` before lookup. Fail closed (flag for manual review) rather than silently defaulting the rate when a country is unknown.
**Suggested Patch:** Replace the prefix chain with a longest-prefix lookup against a maintained code→ISO map; convert leading `00` to `+` first.
**Confidence:** High

---

### Finding 11

**Severity:** Medium
**Title:** `sendBulk` aborts the entire batch on the first invalid number and loses already-sent results
**Category:** Correctness / Reliability
**File:** `SmsChannelService.java:26–28`
**Root Cause:** `messages.stream().map(this::sendSms).toList()`. `sendSms` calls `PhoneNumberValidator.validate`, which throws on a bad number.
**Mechanism:** The stream is eager and unguarded. One invalid recipient anywhere in the list throws `InvalidPhoneNumberException` out of the whole `sendBulk` call. Messages already sent to real providers earlier in the stream have their `SmsResult`s discarded — the caller gets an exception, not a partial result list — while the money for those sends is already spent.
**Production Impact:** A single malformed number in a batch of 1,000 fails the whole call and hides which messages already went out, inviting a full re-send and duplicate charges. No per-message error isolation.
**Likelihood:** Medium — batches routinely contain a bad entry.
**How to Reproduce:** `sendBulk(List.of(valid, invalid, valid))` throws; the first `valid` was already sent but no result is returned.
**Recommended Fix:** Catch per message and return a failure `SmsResult` for that entry instead of throwing, so the batch always returns one result per input. Validate up front and mark invalid entries without aborting siblings.
**Suggested Patch:**
```java
return messages.stream().map(m -> {
    try { return sendSms(m); }
    catch (InvalidPhoneNumberException e) {
        return SmsResult.failure("VALIDATION", "INVALID_NUMBER", e.getMessage());
    }
}).toList();
```
**Confidence:** High

---

### Finding 12

**Severity:** Medium
**Title:** No idempotency; `referenceId` is unused and there is no unique constraint on `message_id` — retries and resends duplicate SMS and double-charge
**Category:** Correctness / Billing / Idempotency
**File:** `SmsMessage.java:9` (`referenceId` field), all gateways (never passed to providers), `db/migration/cs/V1__create_sms_cost_table.sql` (no unique index)
**Root Cause:** `SmsMessage.referenceId` is carried but never used as an idempotency key with any provider, and `sms_cost_record` has no unique constraint tying a logical message to a single row.
**Mechanism:** Once retry/failover exists (Finding 7) — or if a client simply resends — the same logical message is dispatched more than once. Providers that support idempotency keys (Twilio, AWS SNS) are not given one, so they treat each attempt as a new message. Cost recording likewise has no dedup, so the same send can create multiple cost rows.
**Production Impact:** Duplicate SMS to end users (poor experience, possible carrier penalties) and duplicate charges. This risk is latent today only because retry does not exist yet; implementing Finding 7 without this makes it live.
**Likelihood:** Medium, rising to High once retry ships.
**How to Reproduce:** Call `sendSms` twice with the same `referenceId`; two provider requests are made and (once wired) two cost rows written.
**Recommended Fix:** Pass `referenceId` as the provider idempotency key where supported, add a unique constraint on `(tenant_id, message_id)` or `(tenant_id, reference_id)` in `sms_cost_record`, and make `recordCost` upsert/ignore-on-conflict.
**Suggested Patch:** Add `CREATE UNIQUE INDEX ux_sms_cost_msg ON sms_cost_record(tenant_id, message_id);` in a new migration and handle the conflict in `recordCost`.
**Confidence:** Medium

---

### Finding 13

**Severity:** Medium
**Title:** `ProviderRateRegistry` is a shared singleton with mutable `setRate`; any per-tenant rate override bleeds across all tenants
**Category:** Multi-tenancy / Shared mutable state
**File:** `src/main/java/one/formwork/channel/sms/cost/ProviderRateRegistry.java`
**Line(s):** 19 (shared `ConcurrentHashMap`), 42–44 (`setRate`), 34–40 (`getRate`)
**Root Cause:** The registry is a single Spring `@Component` holding one global `rates` map keyed only by `provider::country` — no tenant dimension. `setRate` mutates that shared map at runtime.
**Mechanism:** If per-tenant rate overrides are ever applied through `setRate` (the natural use of a runtime-mutable rate table), the change is global. Tenant A setting a negotiated DE rate silently changes tenant B's billing. `ConcurrentHashMap` makes it thread-safe but not tenant-safe; thread safety is not the issue, isolation is.
**Production Impact:** Cross-tenant billing contamination — one tenant's rate configuration alters another's charges. Violates the brief's rule that one tenant's configuration must never affect another's.
**Likelihood:** Low today (no per-tenant `setRate` caller exists), but the API invites it and the shape is wrong.
**How to Reproduce:** `registry.setRate("TWILIO","DE", x)` for one tenant changes `getRate("TWILIO","DE")` for every tenant.
**Recommended Fix:** Key rates by tenant (`tenantId::provider::country`) with a global default fallback, or make the base rates immutable and hold per-tenant overrides in a tenant-scoped structure. Do not expose a global mutating setter as the per-tenant override path.
**Suggested Patch:** Add an optional tenant dimension to `getRate`/`setRate` and resolve tenant-specific before global.
**Confidence:** Medium

---

### Finding 14

**Severity:** Medium
**Title:** BudgetSMS sends credentials and the message body/recipient as URL query parameters
**Category:** Security / Privacy
**File:** `src/main/java/one/formwork/channel/sms/provider/BudgetSmsGateway.java`
**Line(s):** 25–35
**Root Cause:** The request is a GET with `username`, `password`, `from`, `to`, and `msg` in the query string.
**Mechanism:** Query strings are logged by proxies, load balancers, and access logs far more readily than request bodies, and appear in `WebClient` debug logging. The BudgetSMS password and the recipient number plus full message body travel in the URL. Even over TLS, the URL is exposed at both ends' logging layers.
**Production Impact:** Provider credential and PII exposure through ordinary logging infrastructure. A leaked access log leaks the BudgetSMS password and customer numbers/message content.
**Likelihood:** Medium — depends on logging configuration, but the exposure surface is inherent to putting secrets in URLs.
**How to Reproduce:** Enable `reactor.netty.http.client` debug logging; the full URL including the password is logged.
**Recommended Fix:** BudgetSMS's API is GET-based, so credential-in-URL is partly constrained by the provider, but at minimum ensure these URLs are never logged (mask query params in the logging layer), scope the account to least privilege, and rotate the password. Where the provider offers a POST/body or header-auth variant, use it.
**Confidence:** Medium

---

### Finding 15

**Severity:** Low
**Title:** `handleDeliveryCallback` is an empty no-op; final delivery status is never recorded, so cost is booked at ACCEPTED regardless of eventual delivery
**Category:** Correctness / Observability
**File:** `SmsChannelService.java:30–32`
**Root Cause:** The method body is a comment only. Provider delivery-status webhooks (DELIVERED / FAILED / REJECTED) are accepted and discarded.
**Mechanism:** `SmsResult.isSuccess()` treats `ACCEPTED` as success, and cost (once wired) is recorded at acceptance. If the carrier later rejects the message, nothing updates the record. `DELIVERED`/`SENT` states never materialise because no callback processing writes them.
**Production Impact:** The platform is charged/records cost for messages that were accepted but never delivered, and has no delivery-rate visibility. Reconciliation against provider invoices is impossible.
**Likelihood:** Medium (carrier rejections are normal), Low severity because it is a reconciliation gap rather than an outage.
**How to Reproduce:** Post a delivery callback; nothing changes in the datastore.
**Recommended Fix:** Implement per-provider callback parsing to update the delivery status (and, where a message is ultimately rejected, reverse or flag the cost record). At minimum log/persist the callback for later reconciliation.
**Confidence:** High

---

### Finding 16

**Severity:** Low
**Title:** AWS SNS credentials sourced only from process env vars; single AWS account for all tenants, no IAM role / STS support
**Category:** Security / Configuration / Multi-tenancy
**File:** `AwsSnsSmsGateway.java:63–68`
**Root Cause:** Credentials are read with `System.getenv("AWS_ACCESS_KEY_ID"/"AWS_SECRET_ACCESS_KEY")` on every send. Region comes from config but keys do not.
**Mechanism:** Only long-lived static keys from the process environment are supported. There is no support for the default AWS credential chain (instance profile, IRSA/EKS, STS/assume-role), no rotation, and no per-tenant account separation.
**Production Impact:** Long-lived static keys are a standing credential-theft risk and cannot be rotated without a redeploy. All tenants share one AWS account, so AWS-side SMS spend cannot be attributed per tenant, and blast radius on key compromise is the whole platform.
**Likelihood:** N/A (design weakness).
**How to Reproduce:** Deploy on EKS with an IAM role but no static keys; the gateway returns `CONFIG_ERROR` despite valid role credentials being available.
**Recommended Fix:** Use the AWS SDK's default credential provider chain (or resolve credentials via the platform's secret manager), support assume-role for per-tenant separation, and stop reading raw env vars in the send path.
**Confidence:** Medium

---

## 4. Three Bugs to Fix (highest ROI)

The brief scores Part 1 (review depth) at 40% and Part 2 (fix + prove) at 25%, and it names three harms: *cost real money, leak personal data, fail in production*. The three fixes below map one-to-one onto those harms, are mutually independent, and each has a test that is red on the current code and green after — the exact evidence Part 2 demands.

**Fix A — Finding 1: wire cost recording into the send path (money).**
This is the money bug the brief points at ("find out why [cost recording] doesn't [happen] today"). It is the highest-value single change: it turns an entire dead subsystem live. The failing test is trivial and unambiguous (`verify(costService).recordCost(...)` is red today because there is no call), and the fix is small and low-risk. Best possible ROI.

**Fix B — Finding 2: RFC 3986 encoding in the AWS SNS SigV4 signer (requests failing).**
The brief says "look hardest at the AWS SNS gateway." This is *the* AWS bug, it is subtle (survives a naive reading and all existing tests), and fixing it demonstrates real SigV4 understanding. The failing test — assert the canonical query encodes a space as `%20`, not `+` — is deterministic and needs no live AWS. Fixing it also justifies adding the honest AWS HTTP test the suite is missing (Finding 9).

**Fix C — Finding 3: mask phone numbers in logs (data leaked).**
Direct GDPR/PII fix, the "leak personal data" harm named explicitly. Cheap, isolated, and provable with a Logback `ListAppender` asserting the raw number is absent from log output — red today, green after. High signal for low effort.

Why this trio over the alternatives: Findings 5–8 are all strong, but 5 (segments) and 8 (tenant routing) depend on or overlap Finding 1's wiring and are better shown as Part 3 features; 6 (timeouts) and 7 (retry) are larger, judgment-heavy builds better suited to Part 3. A, B, and C are each self-contained, each cover a distinct named harm, and each yield a clean fails-then-passes test — maximising the Part 2 score for the least risk.

---

## 5. Test Plan

### Fix A — cost recording wired into `sendSms`

- **Failing scenario (red on original):** A valid message is sent, the gateway returns success, yet `SmsCostService.recordCost` is never called and `sms_cost_record` stays empty.
- **Passing scenario (green after fix):** After a successful send, `recordCost` is invoked once with the message's `tenantId`, recipient, and the gateway's `SmsResult`, and a row is persisted.
- **Suggested unit test** (`SmsChannelServiceTest`):
  ```java
  @Test
  void sendSms_success_recordsCostOnce() {
      when(properties.getProvider()).thenReturn("TWILIO");
      when(twilioGateway.supports("TWILIO")).thenReturn(true);
      SmsResult ok = SmsResult.success("SM1", "TWILIO", 2);
      when(twilioGateway.send(any())).thenReturn(ok);
      SmsMessage msg = new SmsMessage("+4915112345678", "Hello there", tenantId);

      service.sendSms(msg);

      verify(costService).recordCost(tenantId, "+4915112345678", ok); // red today: no such call
  }

  @Test
  void sendSms_failure_doesNotRecordCost() {
      when(properties.getProvider()).thenReturn("TWILIO");
      when(twilioGateway.supports("TWILIO")).thenReturn(true);
      when(twilioGateway.send(any())).thenReturn(SmsResult.failure("TWILIO","500","err"));
      service.sendSms(new SmsMessage("+4915112345678", "Hello", tenantId));
      verify(costService, never()).recordCost(any(), any(), any());
  }
  ```
- **Suggested integration test:** `@DataJpaTest` (or Testcontainers Postgres) wiring the real `SmsCostService` + `SmsCostRepository` with a stubbed gateway; assert exactly one `sms_cost_record` row with the correct `tenant_id`, `provider`, `segment_count`, and `total_cost = rate * segments`.

### Fix B — AWS SNS RFC 3986 encoding

- **Failing scenario (red on original):** Encoding the message `"Hello world"` into the canonical query produces `Message=Hello+world`; against AWS this yields `SignatureDoesNotMatch`.
- **Passing scenario (green after fix):** The same message encodes to `Message=Hello%20world`, and the canonical string matches AWS's server-side derivation.
- **Suggested unit test:**
  ```java
  @Test
  void encode_space_usesPercent20_notPlus() {
      String q = AwsSnsSmsGateway.encode("Hello world"); // extract/package-visible for test
      assertEquals("Hello%20world", q);   // red today: returns "Hello+world"
      assertFalse(q.contains("+"));
  }
  ```
- **Suggested integration test** (`MockWebServer`, doubles as Finding 9's coverage): set fixed `AWS_ACCESS_KEY_ID`/`SECRET` and a fixed clock, point the gateway's `WebClient` at the stub, send `"Hello world"`, capture the request, and assert the request-line query contains `Message=Hello%20world` and the `Authorization` header has the `AWS4-HMAC-SHA256 Credential=.../.../sns/aws4_request` shape; return canned SNS XML and assert `MessageId` is parsed.

### Fix C — mask phone numbers in logs

- **Failing scenario (red on original):** Sending to `+4915112345678` writes a log line containing `4915112345678`.
- **Passing scenario (green after fix):** No log line contains the raw national number; the masked form (`+491***78` or similar) appears instead.
- **Suggested unit test** (Logback `ListAppender` on the gateway logger; gateway's `WebClient` mocked to return success):
  ```java
  @Test
  void send_success_doesNotLogRawNumber() {
      ListAppender<ILoggingEvent> appender = attachTo(TwilioSmsGateway.class);
      gateway.send(new SmsMessage("+4915112345678", "Hi", tenantId));
      assertTrue(appender.list.stream()
          .noneMatch(e -> e.getFormattedMessage().contains("4915112345678"))); // red today
  }
  ```
- **Suggested integration test:** Optional — run the send path with a real appender and grep the captured output across all five gateways to prove the masking helper is applied everywhere (guards against one gateway being missed).

### Executed results (red on original, green on fix)

All three fixes were implemented test-first and run locally. Because this extracted folder has no build tool of its own (the module builds inside the `formwork` reactor, whose parent pom and `formwork-base-tenant` artifact are not present here), the tests were executed in an isolated Maven harness: the tenant-independent packages (`api`, `provider`, `validation`, `cost`) plus a minimal `TenantScopedEntity` stub, with dependencies resolved through the Spring Boot BOM. On Java 26, Mockito requires `-Dnet.bytebuddy.experimental=true`. In the real reactor, `mvn -pl formwork-channel-sms test` runs the same tests against the actual base classes.

**Fixed code:** `Tests run: 14, Failures: 0, Errors: 0` — BUILD SUCCESS.

**Original (pre-fix) code:** `Tests run: 14, Failures: 5` — BUILD FAILURE. Failures, one group per fix:

| Fix | Test | Failure on original code |
|-----|------|--------------------------|
| B — AWS SigV4 | `encode_space_usesPercent20_notPlus` | `expected <Hello%20world> but was <Hello+world>` |
| B — AWS SigV4 | `encode_tilde_isUnreservedAndNotEscaped` | `expected <~> but was <%7E>` |
| B — AWS SigV4 | `encode_asterisk_isPercentEncoded` | `expected <%2A> but was <*>` |
| C — PII logs | `send_success_doesNotLogRawRecipient` | raw MSISDN present in log output: `expected <false> but was <true>` |
| A — cost wiring | `sendSms_success_recordsCostOnce` | Mockito `Wanted but not invoked: costService.recordCost(...)` — the send path never called it |

Each test fails on the AI-generated code and passes after the fix, satisfying Part 2's "fails first, passes after" requirement. Commit trail: `05fff5f`/`9ff8fca` (Finding 2), `e31df6d`/`07f28fb` (Finding 3), `c3d2f1b` (Finding 1).

---

## 6. Remaining Improvements (with more time)

- **Retry + failover (Finding 7).** Capped exponential backoff with jitter applied *before* the cap; retry only transient/idempotent-safe failures; failover to a configured secondary provider; parse `RetryProperties.backoff` into a `Duration`.
- **HTTP timeouts and bounded-concurrency bulk (Finding 6).** Connect/read/response timeouts on every `WebClient`; make `sendBulk` bounded-parallel so one slow provider cannot stall a batch.
- **Tenant-aware routing (Finding 8) with strict isolation (Finding 13).** Per-tenant provider resolution from immutable config; rate table keyed by tenant.
- **Real HTTP tests for all five gateways (Findings 4, 9).** Replace the mock-the-client tests with `MockWebServer`/WireMock tests asserting method, path, headers, and body bytes. Delete or rename the misleading `*WireMockTest` files.
- **Idempotency (Finding 12).** Thread `referenceId` to provider idempotency keys; unique constraint on `(tenant_id, message_id)`; upsert in `recordCost`.
- **Accurate segment calculation (Finding 5).** GSM-7/UCS-2 aware segment counting; use provider-returned counts where available.
- **Country-code/rating correctness (Finding 10).** libphonenumber-based country resolution; fail closed on unknown countries rather than silently defaulting the rate.
- **Delivery callbacks (Finding 15).** Implement `handleDeliveryCallback` per provider; reconcile cost against final delivery status.
- **AWS credentials via the default provider chain / STS (Finding 16).** Support IAM roles and rotation; enable per-tenant account separation.
- **Tenant-context propagation guardrails.** If cost recording is ever moved off the hot path (async), ensure the `TenantContext` and the tenant Hibernate filter propagate to the worker thread; otherwise `TenantScopedEntity` auto-population will write rows under the wrong or no tenant. Since `recordCost` currently accepts `tenantId` as a parameter, keep it running inside the request thread and its transaction until this is proven safe.
- **Observability.** Emit per-provider success/latency/error metrics and a delivery-rate metric; add correlation ids so a masked log line can still be traced to a message.

---

*End of review.*
