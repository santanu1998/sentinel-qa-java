# SentinelQA — Enterprise Test Automation Framework

Hybrid **UI + API + database** functional, integration and regression automation for a cloud-hosted
enterprise application, built on **Java 17, Selenium 4, TestNG, REST Assured and Allure**, running
across a dockerised **Selenium Grid** and wired into **GitHub Actions, Jenkins and Azure DevOps**.

[![CI](https://img.shields.io/badge/CI-GitHub%20Actions%20%7C%20Jenkins%20%7C%20Azure%20DevOps-blue)]()
[![Java](https://img.shields.io/badge/Java-17-orange)]()
[![Selenium](https://img.shields.io/badge/Selenium-4.27-green)]()
[![TestNG](https://img.shields.io/badge/TestNG-7.10-red)]()

---

## Why this exists

Most portfolio frameworks stop at "a Selenium test that clicks a button". The problems that actually
make an enterprise suite expensive are elsewhere:

| Problem | What this framework does about it |
|---|---|
| A UI refactor breaks 40 tests overnight | **Self-healing locators** fall through ranked candidates and report the repair instead of failing silently |
| Blanket retries hide real defects | **Retries are gated on a triage verdict** — environmental causes retry, failed business assertions never do |
| "It failed again, no idea why" | Every failure carries a **screenshot, DOM snapshot, request/response pair and a probable-cause verdict** |
| Flaky tests get muted and forgotten | Every result is written to a **SQL table**; the flakiness query names the offenders every run |
| A 200 response is treated as proof of persistence | **Integration tests assert the data store**, not just the HTTP envelope |
| Defect reports are a stack trace pasted into Jira | **Automated defect creation** with triage bucket, probable cause and redacted evidence |

---

## Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Build | Maven |
| UI automation | Selenium WebDriver 4.27, Page Object Model |
| API automation | REST Assured 5.5, JSON Schema validation |
| API exploration | Postman collection + Newman in CI |
| Test runner | TestNG 7.10 (groups, data providers, parallel execution) |
| Reporting | Allure 2.29 |
| Database | H2 / MySQL / PostgreSQL via JDBC |
| Logging | Log4j2 |
| Containers | Docker, Docker Compose (Selenium Grid 4) |
| CI/CD | GitHub Actions, Jenkins (declarative), Azure DevOps |
| Defect tracking | Jira REST API (Azure DevOps work items via the same shape) |

---

## Project layout

```
sentinel-qa-java/
├── pom.xml
├── testng-smoke.xml / testng-api.xml / testng-regression.xml
├── Jenkinsfile · azure-pipelines.yml · .github/workflows/ci.yml
├── docker-compose.grid.yml · Dockerfile
├── docs/
│   ├── TEST_STRATEGY.md              test levels, entry/exit criteria, risk model, metrics
│   └── TEST_CASES_AND_RTM.md         traceability matrix, worked test case, defect log
├── postman/                          collection + environment, run headless by Newman
└── src/
    ├── main/java/com/sentinelqa/
    │   ├── ai/          SmartLocator (self-healing) · AiTriageAdvisor (failure classification)
    │   ├── api/         REST Assured specs, typed clients, request/response models
    │   ├── config/      layered configuration resolution
    │   ├── db/          execution analytics store · SQL validation helper
    │   ├── defects/     Jira / Azure DevOps defect client
    │   ├── driver/      WebDriver factory, thread-confined driver manager
    │   ├── listeners/   evidence capture, verdict-gated retry
    │   ├── pages/       page objects
    │   └── utils/       externalised test data
    └── test/
        ├── java/com/sentinelqa/tests/{ui,api,integration}
        └── resources/{config,testdata,schemas}
```

---

## Running it

**Prerequisites** — JDK 17+, Maven 3.9+, Chrome or Firefox. Docker only if you want the grid.

```bash
# Fast gate: API contract suite, no browser, ~40 seconds
mvn clean test -Dsuite=testng-api.xml

# Smoke suite, headless Chrome
mvn clean test -Dsuite=testng-smoke.xml -Dbrowser=chrome -Dheadless=true

# Full regression across three browsers
mvn clean test -Dsuite=testng-regression.xml

# Against the staging environment on a remote grid
mvn clean test -Dsuite=testng-regression.xml -Denv=staging -Dgrid.enabled=true

# Postman collection, headless
npx newman run postman/SentinelQA_Reservations.postman_collection.json \
    -e postman/qa.postman_environment.json

# Open the report
mvn allure:serve
```

**With the dockerised grid:**

```bash
docker compose -f docker-compose.grid.yml up -d --wait
mvn clean test -Dsuite=testng-regression.xml -Dgrid.enabled=true
docker compose -f docker-compose.grid.yml down
```

---

## Configuration

Resolution order — **`-D` system property → environment variable → `config/<env>.properties` →
`config/framework.properties`**. The same artefact therefore runs unchanged on a laptop, a Jenkins
agent and a GitHub runner; only the outermost layer differs.

| Key | Default | Purpose |
|---|---|---|
| `browser.name` | `chrome` | chrome / firefox / edge |
| `browser.headless` | `true` | Headless in CI, headed when debugging |
| `grid.enabled` / `grid.url` | `false` | Remote execution against Selenium Grid |
| `timeout.explicit` | `20` | Explicit wait budget in seconds |
| `api.sla.millis` | `3000` | Response-time budget asserted on reads |
| `retry.max.attempts` | `2` | Upper bound; the triage verdict decides whether a retry happens at all |
| `db.enabled` / `db.url` | `true` | Execution analytics store |
| `defect.autocreate.enabled` | `false` | Raise a Jira issue per non-environmental failure |

Secrets are never committed. CI supplies `defect.user` and `defect.api.token` from its own secret
store, and the triage layer redacts credential-shaped strings before anything is written to a
report or a ticket.

---

## Coverage

Measured on a local run against the cloud SUTs:

| Suite | Tests | Runtime | Runs on |
|---|---|---|---|
| Smoke (UI + API) | 7 | 23 s | Every pull request |
| API contract | 14 | 16 s | Every pull request, and after each backend deploy |
| Integration + database | 4 | 17 s | Every pull request |
| UI regression (Chrome, 4 threads) | 19 | 43 s | Every pull request |
| Full regression across 3 browsers | 30 cases / 41 executions | ~3 min | Nightly and per release candidate |

Thirty acceptance criteria, thirty automated cases, 100 % traceability — the matrix is in
[`docs/TEST_CASES_AND_RTM.md`](docs/TEST_CASES_AND_RTM.md).

### Defects this framework found in its own build

Worth knowing, because "what did your automation actually catch?" is the question that separates a
real framework from a tutorial:

- **Page objects held in instance fields while TestNG ran methods in parallel on a single class
  instance** — three tests failed with impossible state (a cart showing six items). Fixed by
  building page objects per method.
- **`SmartLocator` resolved candidates with a single immediate lookup**, so an element was found or
  not depending on whether the page happened to finish rendering first. The failure looked like
  locator drift rather than the missing synchronisation it was. Fixed by polling.
- **`addToCart` returned on the click**, not on its effect — on a slow page the click landed before
  the handler was bound and the badge stayed at zero. Fixed by waiting for the control to flip to
  "Remove".
- **REST Assured's `ContentType.JSON` expands `Accept` to four values**, and a service negotiating
  with `res.format()` answered 418 instead of JSON. Fixed by stating one media type.
- **Secret redaction stopped at the auth scheme**, redacting `Bearer` and printing the token after
  it. Fixed so the pattern consumes scheme and value.

---

## Failure triage

Each failure is classified by signature before a human sees it:

| Bucket | Retried | Raises a defect |
|---|---|---|
| `ENVIRONMENT` — host unreachable, grid node lost, 5xx | Yes | No |
| `SYNCHRONISATION` — timeout, stale element, intercepted click | Yes | No |
| `LOCATOR_DRIFT` — selector no longer matches | No | No, fix the page object |
| `API_CONTRACT` — schema or status-code deviation | No | Yes |
| `PRODUCT_DEFECT` — business assertion failed | No | Yes |
| `UNCLASSIFIED` | No | Yes, for human triage |

Classification is deterministic and offline, so a model outage can never change a build result. The
optional LLM step adds a natural-language explanation on top of the verdict the pipeline acts on.

---

## Execution analytics

Every result — pass, fail or skip — is written to `test_execution` with its triage bucket, duration,
environment, browser and linked defect key. That makes questions like these answerable in SQL:

```sql
-- Which tests have both passed and failed recently? (the flaky set)
SELECT test_class, test_name,
       COUNT(*) AS executions,
       SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failures,
       ROUND(100.0 * SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) / COUNT(*), 2) AS failure_pct
FROM test_execution
GROUP BY test_class, test_name
HAVING SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) > 0
   AND SUM(CASE WHEN status = 'PASSED' THEN 1 ELSE 0 END) > 0
ORDER BY failure_pct DESC;
```

The flakiness report is attached to every Allure run, so the offenders are named rather than muted.

---

## CI/CD

All three pipelines run the same suites in the same order — **API gate → browser matrix → Postman →
published report** — so a team can move between them without touching a test.

- **GitHub Actions** (`.github/workflows/ci.yml`) — PR gate, nightly regression, browser matrix,
  Allure history published to GitHub Pages.
- **Jenkins** (`Jenkinsfile`) — parameterised declarative pipeline, grid lifecycle, parallel browser
  stages, Allure plugin, failure email.
- **Azure DevOps** (`azure-pipelines.yml`) — matrix strategy, results published to Azure Test Plans.

---

## Documentation

- [`docs/TEST_STRATEGY.md`](docs/TEST_STRATEGY.md) — levels and ownership, entry/exit criteria,
  risk-based prioritisation, defect lifecycle, Agile/Scrum integration, metrics.
- [`docs/TEST_CASES_AND_RTM.md`](docs/TEST_CASES_AND_RTM.md) — full traceability matrix, a worked
  test case, and the defect log including quarantined known defects.
