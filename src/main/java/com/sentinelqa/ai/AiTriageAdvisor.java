package com.sentinelqa.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classifies a failed test into a triage bucket so the defect raised against it carries a first-pass
 * cause instead of a bare stack trace.
 *
 * <p>Classification runs offline against the exception signature, the recorded
 * {@link SmartLocator.HealEvent}s and the HTTP status captured during the test. The resulting
 * {@link Verdict} drives three things: the Jira issue summary, whether the retry analyzer is allowed
 * to re-run the test, and the "probable cause" line in the Allure report.</p>
 *
 * <p>Set {@code ai.triage.llm.enabled=true} to additionally send the (already redacted) signature to
 * an LLM endpoint for a natural-language explanation; the deterministic verdict below is always the
 * one the pipeline acts on, so a model outage can never change a build result.</p>
 */
public final class AiTriageAdvisor {

    private static final Logger LOG = LoggerFactory.getLogger(AiTriageAdvisor.class);

    /**
     * Consumes the auth scheme along with the value. A pattern that stops at the first whitespace
     * would redact "Bearer" and then print the token that follows it.
     */
    private static final Pattern SECRET = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|token|secret|authorization|api[-_]?key)\\b[\"'\\s:=]*"
                    + "(?:bearer|basic|token)?\\s*[^\\s\"',;)}\\]]+");

    public enum Bucket {
        /** Environment or infrastructure, not the application. Safe to retry. */
        ENVIRONMENT(true),
        /** Timing / synchronisation. Retry once, then report as flaky. */
        SYNCHRONISATION(true),
        /** Locator drift after a UI change. Not a product defect; fix the page object. */
        LOCATOR_DRIFT(false),
        /** Contract or data mismatch coming back from the API. Likely a real defect. */
        API_CONTRACT(false),
        /** Assertion on business behaviour failed. Treat as a product defect. */
        PRODUCT_DEFECT(false),
        /** Nothing matched a known signature. Needs a human. */
        UNCLASSIFIED(false);

        private final boolean retryable;

        Bucket(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    public record Verdict(Bucket bucket, String probableCause, List<String> suggestedActions) {
    }

    private AiTriageAdvisor() {
    }

    public static Verdict triage(String testName, Throwable failure) {
        String signature = redact(signatureOf(failure)).toLowerCase(Locale.ROOT);
        Map<String, SmartLocator.HealEvent> heals = SmartLocator.healEvents();

        if (!heals.isEmpty()) {
            SmartLocator.HealEvent first = heals.values().iterator().next();
            return new Verdict(Bucket.LOCATOR_DRIFT,
                    "A selector was healed during this test, so the page markup changed.",
                    List.of(first.asSuggestion(), "Update the page object and remove the stale fallback."));
        }
        if (containsAny(signature, "unknownhost", "connection refused", "connect timed out",
                "sessionnotcreated", "net::err_", "502", "503", "504")) {
            return new Verdict(Bucket.ENVIRONMENT,
                    "The target environment or grid node was unreachable.",
                    List.of("Check the environment health endpoint before re-running.",
                            "Confirm the Selenium Grid node is registered."));
        }
        if (containsAny(signature, "timeoutexception", "staleelementreference",
                "elementclickintercepted", "elementnotinteractable")) {
            return new Verdict(Bucket.SYNCHRONISATION,
                    "The element was not in a usable state within the wait budget.",
                    List.of("Replace the implicit assumption with an explicit ExpectedCondition.",
                            "Wait for the overlay or spinner to detach before interacting."));
        }
        if (containsAny(signature, "nosuchelement", "no candidate selector matched")) {
            return new Verdict(Bucket.LOCATOR_DRIFT,
                    "No candidate selector matched, so the control was removed or renamed.",
                    List.of("Re-record the locator and add a data-test attribute request to the dev team."));
        }
        if (containsAny(signature, "json schema", "expected status code", "jsonpath",
                "response body doesn't match", "400", "401", "403", "404", "409", "500")) {
            return new Verdict(Bucket.API_CONTRACT,
                    "The API response deviated from the agreed contract.",
                    List.of("Attach the request/response pair to the defect.",
                            "Confirm the contract version deployed to this environment."));
        }
        if (containsAny(signature, "assertionerror", "expected [", "but found [")) {
            return new Verdict(Bucket.PRODUCT_DEFECT,
                    "A business assertion failed with the application reachable and responsive.",
                    List.of("Raise a defect with the exact expected/actual values.",
                            "Confirm the acceptance criterion in the linked user story."));
        }

        LOG.debug("No triage signature matched for {}", testName);
        return new Verdict(Bucket.UNCLASSIFIED,
                "No known failure signature matched.",
                List.of("Review the screenshot, DOM snapshot and logs attached to this result."));
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String signatureOf(Throwable failure) {
        if (failure == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Throwable current = failure;
        int depth = 0;
        while (current != null && depth++ < 5) {
            sb.append(current.getClass().getSimpleName()).append(' ')
              .append(String.valueOf(current.getMessage())).append('\n');
            current = current.getCause();
        }
        return sb.toString();
    }

    /** Never let a credential reach a report, a Jira ticket or an LLM call. */
    public static String redact(String text) {
        return text == null ? "" : SECRET.matcher(text).replaceAll("$1=***REDACTED***");
    }
}
