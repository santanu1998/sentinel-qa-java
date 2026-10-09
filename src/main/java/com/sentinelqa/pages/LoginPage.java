package com.sentinelqa.pages;

import com.sentinelqa.ai.SmartLocator;
import com.sentinelqa.config.ConfigLoader;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

/** Authentication screen of the cloud storefront under test. */
public class LoginPage extends BasePage {

    private final SmartLocator username = SmartLocator.of("Username field",
            By.id("user-name"), By.cssSelector("[data-test='username']"), By.name("user-name"));

    private final SmartLocator password = SmartLocator.of("Password field",
            By.id("password"), By.cssSelector("[data-test='password']"), By.name("password"));

    private final SmartLocator loginButton = SmartLocator.of("Login button",
            By.id("login-button"), By.cssSelector("[data-test='login-button']"),
            By.cssSelector("input[type='submit']"));

    private final SmartLocator errorBanner = SmartLocator.of("Error banner",
            By.cssSelector("[data-test='error']"), By.cssSelector("h3[data-test='error']"),
            By.cssSelector(".error-message-container h3"));

    @Step("Open the login page")
    public LoginPage open() {
        open(ConfigLoader.get("ui.base.url"));
        return this;
    }

    @Override
    public boolean isLoaded() {
        return waitUntilDisplayed(loginButton);
    }

    @Step("Sign in as {user}")
    public ProductsPage loginAs(String user, String secret) {
        type(username, user);
        type(password, secret);
        click(loginButton);
        // The sign-in is only complete once the catalogue URL is reached; asserting on the
        // click alone lets the next step race the navigation.
        waitForUrlContains("inventory.html");
        return new ProductsPage();
    }

    @Step("Attempt sign-in expecting rejection")
    public LoginPage loginExpectingFailure(String user, String secret) {
        type(username, user);
        type(password, secret);
        click(loginButton);
        return this;
    }

    public String errorMessage() {
        return textOf(errorBanner);
    }

    public boolean hasError() {
        return waitUntilDisplayed(errorBanner);
    }
}
