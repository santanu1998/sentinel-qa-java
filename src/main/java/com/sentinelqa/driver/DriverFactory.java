package com.sentinelqa.driver;

import com.sentinelqa.config.ConfigLoader;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;

/**
 * Builds a {@link WebDriver} for the requested browser, either locally (Selenium Manager resolves
 * the driver binary) or against a remote Selenium Grid / cloud grid endpoint.
 *
 * <p>The same test code therefore runs unchanged on a laptop and in a containerised CI job.</p>
 */
public final class DriverFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DriverFactory.class);

    private DriverFactory() {
    }

    public static WebDriver create(BrowserType browser) {
        boolean remote = ConfigLoader.getBoolean("grid.enabled");
        MutableCapabilities options = optionsFor(browser);

        WebDriver driver = remote ? remoteDriver(options) : localDriver(browser, options);

        driver.manage().timeouts().pageLoadTimeout(ConfigLoader.getSeconds("timeout.pageload"));
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ZERO); // explicit waits only
        if (!ConfigLoader.getBoolean("browser.headless")) {
            driver.manage().window().maximize();
        }

        LOG.info("Started {} driver (remote={}, headless={})",
                browser, remote, ConfigLoader.getBoolean("browser.headless"));
        return driver;
    }

    private static WebDriver localDriver(BrowserType browser, MutableCapabilities options) {
        return switch (browser) {
            case CHROME -> new ChromeDriver((ChromeOptions) options);
            case FIREFOX -> new FirefoxDriver((FirefoxOptions) options);
            case EDGE -> new EdgeDriver((EdgeOptions) options);
        };
    }

    private static WebDriver remoteDriver(MutableCapabilities options) {
        String hub = ConfigLoader.get("grid.url");
        try {
            return new RemoteWebDriver(URI.create(hub).toURL(), options);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid Selenium Grid URL: " + hub, e);
        }
    }

    private static MutableCapabilities optionsFor(BrowserType browser) {
        boolean headless = ConfigLoader.getBoolean("browser.headless");
        String window = ConfigLoader.get("browser.window.size", "1920,1080");

        switch (browser) {
            case CHROME -> {
                ChromeOptions chrome = new ChromeOptions();
                if (headless) {
                    chrome.addArguments("--headless=new");
                }
                chrome.addArguments("--window-size=" + window,
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--remote-allow-origins=*",
                        "--disable-search-engine-choice-screen");
                chrome.setAcceptInsecureCerts(true);
                return chrome;
            }
            case FIREFOX -> {
                FirefoxOptions firefox = new FirefoxOptions();
                if (headless) {
                    firefox.addArguments("-headless");
                }
                firefox.addArguments("--width=" + window.split(",")[0], "--height=" + window.split(",")[1]);
                firefox.setAcceptInsecureCerts(true);
                return firefox;
            }
            case EDGE -> {
                EdgeOptions edge = new EdgeOptions();
                if (headless) {
                    edge.addArguments("--headless=new");
                }
                edge.addArguments("--window-size=" + window, "--no-sandbox", "--disable-dev-shm-usage");
                edge.setAcceptInsecureCerts(true);
                return edge;
            }
            default -> throw new IllegalArgumentException("Unhandled browser: " + browser);
        }
    }
}
