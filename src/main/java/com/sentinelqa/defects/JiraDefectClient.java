package com.sentinelqa.defects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelqa.ai.AiTriageAdvisor;
import com.sentinelqa.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Raises a defect in Jira (or an Azure DevOps work item, via the same shape) when a test fails with
 * a triage verdict that points at the product rather than the environment.
 *
 * <p>Two deliberate constraints: only non-retryable verdicts create tickets, so the backlog does not
 * fill with infrastructure noise; and the description is redacted before it leaves the process, so a
 * credential captured in a stack trace never lands in a tracker.</p>
 *
 * <p>Disabled by default ({@code defect.autocreate.enabled=false}). Enable it per pipeline once the
 * suite is stable enough that a failure genuinely means a defect.</p>
 */
public final class JiraDefectClient {

    private static final Logger LOG = LoggerFactory.getLogger(JiraDefectClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public boolean isEnabled() {
        return ConfigLoader.getBoolean("defect.autocreate.enabled");
    }

    /**
     * @return the created issue key (e.g. {@code QA-1423}), or empty when auto-creation is off,
     *         the verdict is retryable, or the tracker rejected the call.
     */
    public Optional<String> raise(String testName, AiTriageAdvisor.Verdict verdict, Throwable failure) {
        if (!isEnabled()) {
            LOG.debug("Defect auto-creation disabled; skipping ticket for {}", testName);
            return Optional.empty();
        }
        if (verdict.bucket().isRetryable()) {
            LOG.info("Verdict {} is environmental; not raising a defect for {}", verdict.bucket(), testName);
            return Optional.empty();
        }

        String summary = "[%s][%s] %s".formatted(
                ConfigLoader.get("defect.project.key"),
                verdict.bucket(),
                testName);

        String description = AiTriageAdvisor.redact("""
                *Automated defect raised by SentinelQA*

                *Environment:* %s
                *Triage bucket:* %s
                *Probable cause:* %s

                *Suggested next steps:*
                %s

                *Failure:*
                {code}
                %s
                {code}
                """.formatted(
                ConfigLoader.activeEnvironment(),
                verdict.bucket(),
                verdict.probableCause(),
                bullets(verdict.suggestedActions()),
                failure == null ? "n/a" : failure.toString()));

        try {
            String payload = MAPPER.writeValueAsString(Map.of(
                    "fields", Map.of(
                            "project", Map.of("key", ConfigLoader.get("defect.project.key")),
                            "summary", summary,
                            "description", description,
                            "issuetype", Map.of("name", "Bug"),
                            "labels", List.of("automation", "sentinelqa",
                                    verdict.bucket().name().toLowerCase()))));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ConfigLoader.get("defect.base.url") + "/rest/api/2/issue"))
                    .header("Authorization", basicAuth())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                String key = MAPPER.readTree(response.body()).path("key").asText();
                LOG.info("Raised defect {} for {}", key, testName);
                return Optional.of(key);
            }
            LOG.warn("Tracker rejected defect creation (HTTP {}): {}", response.statusCode(), response.body());
        } catch (Exception e) {
            // A tracker outage must never turn a green suite red.
            Thread.currentThread().interrupt();
            LOG.warn("Defect creation failed for {}", testName, e);
        }
        return Optional.empty();
    }

    private static String basicAuth() {
        String raw = ConfigLoader.get("defect.user") + ":" + ConfigLoader.get("defect.api.token");
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String bullets(List<String> items) {
        return items.stream().map(i -> "* " + i).reduce((a, b) -> a + "\n" + b).orElse("* n/a");
    }
}
