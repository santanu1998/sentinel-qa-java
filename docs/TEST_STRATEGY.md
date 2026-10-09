# Test Strategy — SentinelQA

Version 1.0 · Owner: QA Engineering · Review cadence: per release train

---

## 1. Purpose and scope

This document states how the reservations platform is verified: what is tested, at which layer, by
whom, and what evidence a release decision rests on.

**In scope** — functional, integration, regression and contract testing of the storefront UI, the
reservations REST API, and the data store behind it, across the `qa` and `staging` cloud
environments.

**Out of scope** — load and soak testing (separate k6 pipeline), penetration testing (annual third
party engagement), and localisation beyond `en-IN`.

---

## 2. Test levels and ownership

| Level | What it proves | Owner | Trigger | Budget |
|---|---|---|---|---|
| Unit | A class behaves as written | Developer | Pre-commit | < 2 min |
| **API contract** | Endpoints honour their published schema and status codes | **QA** | Every PR | < 1 min |
| **Integration** | An API call produces the state it claims to | **QA** | Every PR | < 3 min |
| **Functional UI** | A user can complete each supported journey | **QA** | Every PR (smoke) | < 5 min |
| **Regression** | Nothing previously working has broken | **QA** | Nightly + RC | < 25 min |
| Exploratory | Risks the scripted suite does not model | Whole team | Per story | Timeboxed 90 min |

The shape is deliberate. The cheapest layer that can catch a defect owns it: a field rename is a
schema test, not a browser test. Browser tests are reserved for journeys a user actually performs.

---

## 3. Entry and exit criteria

**Entry to a test cycle**
- The build is deployed to the target environment and `/ping` returns 201.
- All acceptance criteria on in-scope stories are written and agreed.
- Test data is seeded and the environment banner shows the expected build number.

**Exit from a test cycle**
- 100 % of smoke and API-contract tests pass.
- ≥ 98 % of the regression suite passes; every remaining failure is linked to a triaged defect.
- No open Critical or Blocker defect against an in-scope story.
- The flakiness report shows no test with a failure rate above 5 % over the last 10 runs.
- The traceability matrix shows every acceptance criterion mapped to at least one test case.

---

## 4. Risk-based prioritisation

Coverage is allocated by `impact × likelihood`, not evenly.

| Area | Impact | Likelihood | Priority | Coverage |
|---|---|---|---|---|
| Checkout and order totals | High | Medium | **P0** | E2E + arithmetic assertions + API contract |
| Authentication and session | High | Low | **P0** | Positive, negative, lockout, injection payloads |
| Booking CRUD | High | Medium | **P0** | Full lifecycle + schema + persistence check |
| Authorisation on mutations | High | Low | **P0** | Unauthenticated DELETE/PUT must be refused |
| Catalogue sorting and filters | Medium | High | P1 | Data-driven across every sort option |
| Cart manipulation | Medium | Medium | P1 | Add, remove, badge sync |
| Static content | Low | Low | P3 | Exploratory only |

---

## 5. Defect lifecycle

```
NEW ──► TRIAGED ──► IN PROGRESS ──► FIXED ──► RETEST ──► CLOSED
 │          │                                    │
 │          └──► DEFERRED (backlog, with reason) │
 └──► REJECTED (not a defect / duplicate) ◄──────┘  REOPENED
```

Every automated failure is classified before it reaches a human (see §7). Only non-environmental
verdicts create a Jira issue, which keeps the backlog a list of product problems rather than a log
of infrastructure hiccups.

**Severity** is the effect on the user: Blocker (no workaround on a P0 journey), Critical (data loss
or wrong money), Major (feature broken, workaround exists), Minor (cosmetic).
**Priority** is the order of work and is set by the product owner, not by QA.

---

## 6. Agile/Scrum integration

- **Sprint planning** — QA sizes test design alongside development estimates; a story without
  testable acceptance criteria is not accepted into the sprint.
- **Three amigos** — business analyst, developer and tester agree examples before code is written.
  Ambiguity found here costs minutes; found in UAT it costs days.
- **Daily stand-up** — QA reports blocked verification and raises quality risk early rather than at
  the end of the sprint.
- **Definition of Done** — merged, automated test added at the correct level, regression green,
  no new Critical defect, documentation updated.
- **Retrospective** — escaped defects are reviewed for the missing test level, not for blame.

---

## 7. Failure triage and retry policy

A failed test is classified into one of six buckets by signature:

| Bucket | Cause | Retryable | Raises a defect |
|---|---|---|---|
| `ENVIRONMENT` | Host unreachable, grid node lost, 5xx gateway | Yes | No |
| `SYNCHRONISATION` | Timeout, stale element, intercepted click | Yes | No |
| `LOCATOR_DRIFT` | Selector no longer matches | No | No — fix the page object |
| `API_CONTRACT` | Schema or status-code deviation | No | **Yes** |
| `PRODUCT_DEFECT` | Business assertion failed | No | **Yes** |
| `UNCLASSIFIED` | Unknown signature | No | **Yes**, for human triage |

Retries are gated on the verdict. A blanket "retry three times" policy converts real defects into
green builds; this one keeps the suite stable without spending the signal.

---

## 8. Test data management

- Data lives in versioned JSON under `src/test/resources/testdata`, never inline in a test method.
- Every API test creates the record it needs and does not depend on another test's leftovers.
- Unique identifiers are timestamp-suffixed so parallel workers cannot collide.
- Credentials come from the CI secret store; nothing secret is committed, and the triage layer
  redacts credential-shaped strings before any report or ticket is written.

---

## 9. Environments

| Environment | Purpose | Grid | Data |
|---|---|---|---|
| `local` | Authoring and debugging | Local browser | Disposable |
| `qa` | PR gate and nightly regression | Dockerised Grid 4 | Reset nightly |
| `staging` | Release-candidate sign-off | Cloud grid | Production-like, anonymised |

The same binary runs in all three; only `config/<env>.properties` differs.

---

## 10. Reporting and metrics

Published every run: Allure report with screenshots, DOM snapshots, request/response pairs and the
triage verdict on each failure.

Tracked over time, from the execution analytics table:

- **Defect escape rate** — defects found in production ÷ total defects. Target < 5 %.
- **Automation pass rate** — target ≥ 98 % on the nightly regression.
- **Flakiness** — tests with mixed pass/fail history. Target: zero above 5 %.
- **Mean time to feedback** — commit to suite verdict. Target < 10 minutes for the PR gate.
- **Requirement coverage** — acceptance criteria with at least one linked test. Target 100 %.

---

## 11. Risks to this strategy

| Risk | Mitigation |
|---|---|
| UI selectors churn faster than tests are maintained | Self-healing locators plus a standing request for `data-test` attributes |
| Shared environment data mutated by another team | Each test creates its own data; no reliance on seeded records |
| Suite runtime grows past the PR budget | Layer discipline and parallel execution; move coverage down the pyramid |
| Flaky tests erode trust in the gate | Flakiness tracked in SQL and fixed as defects, not muted |
