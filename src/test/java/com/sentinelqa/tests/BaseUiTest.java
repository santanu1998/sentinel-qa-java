package com.sentinelqa.tests;

import com.sentinelqa.config.ConfigLoader;
import com.sentinelqa.driver.BrowserType;
import com.sentinelqa.driver.DriverFactory;
import com.sentinelqa.driver.DriverManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;

import java.lang.reflect.Method;

/**
 * One browser session per test method, confined to the executing thread.
 *
 * <p>Method-level isolation costs a few seconds of start-up and buys independence: any test can run
 * alone, in any order, in parallel — which is the only way a regression suite stays trustworthy as
 * it grows.</p>
 */
public abstract class BaseUiTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @BeforeMethod(alwaysRun = true)
    @Parameters({"browser"})
    public void startBrowser(@Optional("") String suiteBrowser, Method method) {
        String requested = suiteBrowser.isBlank()
                ? ConfigLoader.get("browser.name", "chrome")
                : suiteBrowser;
        DriverManager.set(DriverFactory.create(BrowserType.from(requested)));
        log.debug("Browser ready for {}", method.getName());
    }

    @AfterMethod(alwaysRun = true)
    public void stopBrowser() {
        DriverManager.quit();
    }
}
