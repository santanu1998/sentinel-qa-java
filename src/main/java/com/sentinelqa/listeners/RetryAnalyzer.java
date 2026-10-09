package com.sentinelqa.listeners;

import com.sentinelqa.ai.AiTriageAdvisor;
import com.sentinelqa.config.ConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retries a failed test only when triage says the cause was environmental or timing-related.
 *
 * <p>Blanket retries hide real defects: a test that fails on a wrong total and passes on the second
 * attempt still found a bug. Gating retries on the triage verdict keeps the suite stable without
 * buying that stability with lost signal.</p>
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private static final Logger LOG = LoggerFactory.getLogger(RetryAnalyzer.class);
    private static final Map<String, Integer> ATTEMPTS = new ConcurrentHashMap<>();

    @Override
    public boolean retry(ITestResult result) {
        int max = ConfigLoader.getInt("retry.max.attempts");
        if (max <= 0) {
            return false;
        }

        String key = result.getTestClass().getName() + "#" + result.getMethod().getMethodName();
        AiTriageAdvisor.Verdict verdict = AiTriageAdvisor.triage(key, result.getThrowable());

        if (!verdict.bucket().isRetryable()) {
            LOG.info("Not retrying {}: verdict {} indicates a genuine failure", key, verdict.bucket());
            return false;
        }

        int attempt = ATTEMPTS.merge(key, 1, Integer::sum);
        if (attempt <= max) {
            LOG.warn("Retrying {} (attempt {}/{}) after {} failure", key, attempt, max, verdict.bucket());
            return true;
        }
        LOG.error("{} exhausted {} retries; reporting as failed", key, max);
        return false;
    }

    public static int attemptsFor(String key) {
        return ATTEMPTS.getOrDefault(key, 0);
    }

    public static void reset() {
        ATTEMPTS.clear();
    }
}
