package com.sentinelqa.pages;

import com.sentinelqa.ai.SmartLocator;
import com.sentinelqa.config.ConfigLoader;
import com.sentinelqa.driver.DriverManager;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Shared interaction layer for every page object.
 *
 * <p>All synchronisation is explicit. No {@code Thread.sleep}, no implicit wait: those are the two
 * habits that turn a regression suite into a coin flip.</p>
 */
public abstract class BasePage {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final WebDriver driver;
    protected final WebDriverWait wait;

    protected BasePage() {
        this.driver = DriverManager.get();
        this.wait = new WebDriverWait(driver, ConfigLoader.getSeconds("timeout.explicit"));
    }

    /** Every page object answers one question: am I actually loaded? */
    public abstract boolean isLoaded();

    @Step("Open {url}")
    protected void open(String url) {
        log.info("Navigating to {}", url);
        driver.get(url);
    }

    @Step("Click {locator.name}")
    protected void click(SmartLocator locator) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                WebElement element = locator.resolve(driver);
                scrollIntoView(element);
                wait.until(ExpectedConditions.elementToBeClickable(element)).click();
                return;
            } catch (RuntimeException e) {
                if (attempt == 3) {
                    throw e;
                }
                try {
                    WebElement element = locator.resolve(driver);
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                    return;
                } catch (RuntimeException ignored) {
                    log.debug("Click retry {} for {} after transient failure: {}", attempt, locator.name(), e.toString());
                }
            }
        }
    }

    @Step("Type into {locator.name}")
    protected void type(SmartLocator locator, String text) {
        WebElement element = wait.until(ExpectedConditions.visibilityOf(locator.resolve(driver)));
        element.clear();
        element.sendKeys(text);
    }

    @Step("Read text of {locator.name}")
    protected String textOf(SmartLocator locator) {
        return wait.until(ExpectedConditions.visibilityOf(locator.resolve(driver))).getText().trim();
    }

    protected List<String> textsOf(SmartLocator locator) {
        return locator.resolveAll(driver).stream().map(WebElement::getText).map(String::trim).toList();
    }

    /**
     * Presence check on a short budget.
     *
     * <p>"Is the cart badge showing?" has a legitimate answer of <em>no</em>, and that answer must
     * not cost a full explicit-wait timeout every time the cart is empty.</p>
     */
    protected boolean isDisplayed(SmartLocator locator) {
        return locator.isPresent(driver);
    }

    /** Presence check on the full explicit-wait budget, for an element that should be arriving. */
    protected boolean waitUntilDisplayed(SmartLocator locator) {
        try {
            return wait.until(ExpectedConditions.visibilityOf(locator.resolve(driver))) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    protected boolean waitForVisible(By by) {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(by));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    protected void waitForUrlContains(String fragment) {
        wait.until(ExpectedConditions.urlContains(fragment));
    }

    protected void scrollIntoView(WebElement element) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'});", element);
    }

    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    public String title() {
        return driver.getTitle();
    }
}
