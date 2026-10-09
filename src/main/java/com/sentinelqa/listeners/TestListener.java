package com.sentinelqa.listeners;

import com.sentinelqa.ai.AiTriageAdvisor;
import com.sentinelqa.ai.SmartLocator;
import com.sentinelqa.config.ConfigLoader;
import com.sentinelqa.db.ExecutionRepository;
import com.sentinelqa.defects.JiraDefectClient;
import com.sentinelqa.driver.DriverManager;
import io.qameta.allure.Allure;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Single place where a result becomes evidence: screenshot, page source, triage verdict,
 * analytics row and — when enabled — a defect in the tracker.
 */
public class TestListener implements ITestListener, ISuiteListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestListener.class);
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    private final JiraDefectClient defectClient = new JiraDefectClient();

    @Override
    public void onStart(ISuite suite) {
        LOG.info("=== Run {} | suite '{}' | env '{}' | browser '{}' ===",
                RUN_ID, suite.getName(), ConfigLoader.activeEnvironment(),
                ConfigLoader.get("browser.name", "n/a"));
        ExecutionRepository.initialise();
        SmartLocator.clearHealEvents();
    }

    @Override
    public void onFinish(ISuite suite) {
        List<ExecutionRepository.FlakyTest> flaky = ExecutionRepository.flakyTests();
        if (!flaky.isEmpty()) {
            StringBuilder sb = new StringBuilder("Tests with mixed pass/fail history:\n");
            flaky.forEach(f -> sb.append("  %s#%s — %d/%d failed (%.2f%%)%n".formatted(
                    f.testClass(), f.testName(), f.failures(), f.executions(), f.failurePercentage())));
            LOG.warn(sb.toString());
            Allure.addAttachment("Flakiness report", "text/plain", sb.toString());
        }
        if (!SmartLocator.healEvents().isEmpty()) {
            String suggestions = SmartLocator.healEvents().values().stream()
                    .map(SmartLocator.HealEvent::asSuggestion)
                    .reduce((a, b) -> a + "\n" + b).orElse("");
            Allure.addAttachment("Locator repairs to apply", "text/plain", suggestions);
            LOG.warn("Locators healed during this run:\n{}", suggestions);
        }
    }

    @Override
    public void onTestStart(ITestResult result) {
        LOG.info("▶ {}", qualified(result));
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        LOG.info("✔ {} ({} ms)", qualified(result), duration(result));
        persist(result, "PASSED", null, null);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        String name = qualified(result);
        Throwable failure = result.getThrowable();
        LOG.error("✘ {} — {}", name, failure == null ? "no throwable" : failure.getMessage());

        captureScreenshot(name);
        capturePageSource();

        AiTriageAdvisor.Verdict verdict = AiTriageAdvisor.triage(name, failure);
        Allure.addAttachment("Triage verdict", "text/plain",
                "Bucket: %s%nProbable cause: %s%nSuggested actions:%n - %s".formatted(
                        verdict.bucket(), verdict.probableCause(),
                        String.join("\n - ", verdict.suggestedActions())));

        String defectKey = defectClient.raise(name, verdict, failure).orElse(null);
        persist(result, "FAILED", verdict, defectKey);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        LOG.warn("⤼ {} skipped", qualified(result));
        persist(result, "SKIPPED", null, null);
    }

    @Override
    public void onFinish(ITestContext context) {
        LOG.info("Suite '{}' finished — passed {}, failed {}, skipped {}",
                context.getName(),
                context.getPassedTests().size(),
                context.getFailedTests().size(),
                context.getSkippedTests().size());
    }

    private void captureScreenshot(String testName) {
        if (!DriverManager.isActive()) {
            return;
        }
        try {
            byte[] png = ((TakesScreenshot) DriverManager.get()).getScreenshotAs(OutputType.BYTES);
            Allure.addAttachment("Screenshot — " + testName, new ByteArrayInputStream(png));
        } catch (RuntimeException e) {
            LOG.debug("Screenshot capture failed", e);
        }
    }

    private void capturePageSource() {
        if (!DriverManager.isActive()) {
            return;
        }
        try {
            Allure.addAttachment("DOM snapshot", "text/html", DriverManager.get().getPageSource(), ".html");
        } catch (RuntimeException e) {
            LOG.debug("Page source capture failed", e);
        }
    }

    private void persist(ITestResult result, String status,
                         AiTriageAdvisor.Verdict verdict, String defectKey) {
        ExecutionRepository.record(new ExecutionRepository.ExecutionRecord(
                RUN_ID,
                result.getTestContext().getSuite().getName(),
                result.getTestClass().getName(),
                result.getMethod().getMethodName(),
                layerOf(result),
                ConfigLoader.activeEnvironment(),
                ConfigLoader.get("browser.name", null),
                status,
                duration(result),
                verdict == null ? null : verdict.bucket().name(),
                result.getThrowable() == null ? null
                        : AiTriageAdvisor.redact(result.getThrowable().toString()),
                defectKey,
                Instant.now()));
    }

    private static String layerOf(ITestResult result) {
        String pkg = result.getTestClass().getName();
        if (pkg.contains(".api.")) {
            return "API";
        }
        if (pkg.contains(".integration.")) {
            return "INTEGRATION";
        }
        return "UI";
    }

    private static long duration(ITestResult result) {
        return result.getEndMillis() - result.getStartMillis();
    }

    private static String qualified(ITestResult result) {
        return result.getTestClass().getRealClass().getSimpleName() + "#" + result.getMethod().getMethodName();
    }
}
