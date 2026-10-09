package com.sentinelqa.driver;

import org.openqa.selenium.WebDriver;

/**
 * Thread-confined WebDriver holder. Each TestNG worker thread owns exactly one browser session,
 * which is what makes parallel regression runs safe.
 */
public final class DriverManager {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {
    }

    public static void set(WebDriver driver) {
        DRIVER.set(driver);
    }

    public static WebDriver get() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException(
                    "No WebDriver bound to thread " + Thread.currentThread().getName()
                            + ". Did the test extend BaseUiTest?");
        }
        return driver;
    }

    public static boolean isActive() {
        return DRIVER.get() != null;
    }

    public static void quit() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                DRIVER.remove();
            }
        }
    }
}
