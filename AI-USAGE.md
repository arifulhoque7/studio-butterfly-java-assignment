# AI-USAGE.md

I used Claude Code heavily throughout this submission, and reviewed everything it produced. This file
is the honest account.

## What I used AI for, and how

- **Reading the module.** I had the agent read every source and test file first and build a map of the
  send path before proposing anything, so the review was grounded in the actual code rather than
  pattern-matching on file names.
- **Drafting `REVIEW.md`.** The agent produced the first pass of the findings in the required format. I
  drove which defects were real, how they ranked, and cut the ones that were cosmetic. The framing of
  the three headline bugs around the assignment's own three harms (money / outage / PII) was a
  deliberate editorial choice I made, not something the model volunteered.
- **The fixes and their tests.** The agent wrote the mechanical parts — the RFC 3986 encoder, the
  `PhoneMasker`, the segment calculator tables, the test scaffolding, the commit messages. I specified
  the test-first (red → green) structure and verified each red state by actually reverting the
  production change and re-running.
- **The verification harness and CI.** The agent set up the standalone Maven harness, the source-copy
  script, and the GitHub Actions workflow.

Everything was run before it was committed: the full suite passes (180 tests) and the coverage gate is
met. I did not commit anything I had not seen execute.

## Where the AI was wrong — a concrete case

**The JaCoCo coverage gate silently measured nothing.**

When wiring the coverage gate into the harness, the agent's first `maven-surefire-plugin` configuration
set the test JVM arguments like this:

```xml
<argLine>-Dnet.bytebuddy.experimental=true -XX:+EnableDynamicAgentLoading</argLine>
```

That looks correct, and the build was green. But it is wrong in a way that defeats the entire point of
the gate. JaCoCo's `prepare-agent` goal injects the coverage agent by *setting the Maven property
`argLine`*. Surefire then uses that property as its JVM arguments. By hardcoding `<argLine>` with a
literal string, this configuration **overwrites** the property JaCoCo set — so the coverage agent never
attaches, no execution data is produced, and the `check` goal passes vacuously against zero data. A
green build that proves nothing is worse than a red one.

The fix is to *append* to JaCoCo's value rather than replace it:

```xml
<argLine>@{argLine} -Dnet.bytebuddy.experimental=true -XX:+EnableDynamicAgentLoading</argLine>
```

`@{argLine}` is Surefire's late-property expansion of the `argLine` that JaCoCo populated. With it, the
agent attaches, real coverage is measured, and the 70% gate actually bites. I caught this because the
first run reported "All coverage checks have been met" implausibly fast with no `jacoco.exec` worth
speaking of, which did not match a suite that exercises signing, retry, and the send path.

This is exactly the class of bug the assignment is about: plausible, green, and quietly meaningless. It
is also a good argument for the review discipline the role is hiring for — the failure was invisible
unless you asked "would this check fail if coverage were actually zero?"

## What I wrote or drove myself because I did not trust the agent with it

- **The SigV4 diagnosis.** The agent could produce an RFC 3986 encoder on request, but the reasoning
  that the bug is invisible *because the same wrong encoder is used for signing and for the request*,
  and that the mismatch is created server-side by AWS's re-canonicalization, is the part that matters
  and the part I verified against the SigV4 spec myself. A fix without that understanding is a guess.
- **The retry backoff semantics.** I specified jitter-before-cap explicitly (the assignment's own
  example warns about jitter-after-cap making the cap meaningless), and the classification of which
  errors are retryable — transient network/timeout, 429, 5xx; never deterministic 4xx or config errors,
  because retrying those wastes time and, without idempotency, risks duplicate sends.
- **Scope and cuts.** What to build fully versus document (timeouts, idempotency, per-tenant rate
  isolation) was my call, recorded in `README.md`, not the agent's default of trying to do everything.

I can defend any line in this submission.
