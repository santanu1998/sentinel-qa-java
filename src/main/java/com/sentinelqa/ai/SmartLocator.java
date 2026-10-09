package com.sentinelqa.ai;

import com.sentinelqa.config.ConfigLoader;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.FluentWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Self-healing element resolution.
 *
 * <p>A page object declares a ranked list of locator candidates for one logical control. When the
 * primary selector stops matching — the usual cause of regression-suite noise after a UI refactor —
 * the locator falls through to the next candidate, records a <em>heal event</em>, and lets the test
 * continue. Heal events are surfaced in the run report so the team fixes the selector deliberately
 * instead of discovering it as a flaky failure days later.</p>
 *
 * <p>The ranking is deterministic and auditable; {@link AiTriageAdvisor} consumes the recorded heal
 * events to suggest the replacement selector in the run summary.</p>
 */
public final class SmartLocator {

    private static final Logger LOG = LoggerFactory.getLogger(SmartLocator.class);
    private static final Map<String, HealEvent> HEAL_LOG = new LinkedHashMap<>();

    private final String name;
    private final List<By> candidates;

    private SmartLocator(String name, List<By> candidates) {
        this.name = name;
        this.candidates = candidates;
    }

    public static SmartLocator of(String name, By primary, By... fallbacks) {
        List<By> all = new ArrayList<>();
        all.add(primary);
        all.addAll(Arrays.asList(fallbacks));
        return new SmartLocator(name, List.copyOf(all));
    }

    public By primary() {
        return candidates.get(0);
    }

    public String name() {
        return name;
    }

    /** Probe budget for "is this on screen?" checks, where absence is a legitimate answer. */
    private static final Duration PROBE = Duration.ofMillis(600);

    /**
     * Resolves the first candidate that matches, healing silently but never invisibly.
     *
     * <p>Resolution <em>polls</em>. A single immediate lookup is the subtlest flakiness source in a
     * locator layer: the element is found or not found depending on whether the page happened to
     * finish rendering first, and the resulting failure looks like locator drift rather than the
     * missing synchronisation it actually is.</p>
     */
    public WebElement resolve(SearchContext context) {
        return resolve(context, Duration.ofSeconds(ConfigLoader.getInt("timeout.explicit")));
    }

    public WebElement resolve(SearchContext context, Duration timeout) {
        FluentWait<SearchContext> wait = new FluentWait<>(context)
                .withTimeout(timeout)
                .pollingEvery(Duration.ofMillis(250))
                .ignoring(StaleElementReferenceException.class);
        try {
            return wait.until(this::firstMatch);
        } catch (TimeoutException e) {
            throw new NoSuchElementException(
                    "No candidate selector matched for '" + name + "' within " + timeout.toSeconds()
                            + "s. Tried: " + candidates, e);
        }
    }

    /** Short-budget variant for presence checks, so "absent" does not cost a full wait. */
    public boolean isPresent(SearchContext context) {
        return firstMatchQuietly(context, PROBE) != null;
    }

    public List<WebElement> resolveAll(SearchContext context) {
        // Wait for the first candidate to produce anything, then return the whole match set.
        if (firstMatchQuietly(context, Duration.ofSeconds(ConfigLoader.getInt("timeout.explicit"))) == null) {
            return List.of();
        }
        for (By candidate : candidates) {
            List<WebElement> found = context.findElements(candidate);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return List.of();
    }

    private WebElement firstMatchQuietly(SearchContext context, Duration timeout) {
        try {
            return resolve(context, timeout);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return the first matching element, or null so {@link FluentWait} keeps polling. */
    private WebElement firstMatch(SearchContext context) {
        for (int i = 0; i < candidates.size(); i++) {
            By candidate = candidates.get(i);
            List<WebElement> found = context.findElements(candidate);
            if (!found.isEmpty()) {
                if (i > 0 && !HEAL_LOG.containsKey(name)) {
                    HEAL_LOG.put(name, new HealEvent(name, candidates.get(0).toString(), candidate.toString()));
                    LOG.warn("Self-healed '{}': primary [{}] failed, matched fallback [{}]",
                            name, candidates.get(0), candidate);
                }
                return found.get(0);
            }
        }
        return null;
    }

    public static Map<String, HealEvent> healEvents() {
        return Map.copyOf(HEAL_LOG);
    }

    public static void clearHealEvents() {
        HEAL_LOG.clear();
    }

    /** One recorded locator repair, used by the triage advisor and the Allure summary. */
    public record HealEvent(String elementName, String brokenSelector, String workingSelector) {

        public String asSuggestion() {
            return "Element '%s': replace %s with %s".formatted(elementName, brokenSelector, workingSelector);
        }
    }
}
