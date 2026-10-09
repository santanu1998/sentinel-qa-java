package com.sentinelqa.pages;

import com.sentinelqa.ai.SmartLocator;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

import java.math.BigDecimal;

/** Three-step checkout: customer details, order summary, confirmation. */
public class CheckoutPage extends BasePage {

    private final SmartLocator firstName = SmartLocator.of("First name",
            By.cssSelector("[data-test='firstName']"), By.id("first-name"));
    private final SmartLocator lastName = SmartLocator.of("Last name",
            By.cssSelector("[data-test='lastName']"), By.id("last-name"));
    private final SmartLocator postalCode = SmartLocator.of("Postal code",
            By.cssSelector("[data-test='postalCode']"), By.id("postal-code"));
    private final SmartLocator continueButton = SmartLocator.of("Continue",
            By.cssSelector("[data-test='continue']"),
            By.xpath("//button[normalize-space(.)='Continue' or @id='continue' or @value='Continue']"),
            By.cssSelector("input[value='Continue']"));
    private final SmartLocator finishButton = SmartLocator.of("Finish",
            By.cssSelector("[data-test='finish']"),
            By.xpath("//button[normalize-space(.)='Finish' or @id='finish' or @value='Finish']"),
            By.cssSelector("input[value='Finish']"));
    private final SmartLocator errorBanner = SmartLocator.of("Validation error",
            By.cssSelector("[data-test='error']"), By.cssSelector(".error-message-container h3"));
    private final SmartLocator subtotalLabel = SmartLocator.of("Item subtotal",
            By.cssSelector("[data-test='subtotal-label']"), By.className("summary_subtotal_label"));
    private final SmartLocator taxLabel = SmartLocator.of("Tax",
            By.cssSelector("[data-test='tax-label']"), By.className("summary_tax_label"));
    private final SmartLocator totalLabel = SmartLocator.of("Order total",
            By.cssSelector("[data-test='total-label']"), By.className("summary_total_label"));
    private final SmartLocator confirmationHeader = SmartLocator.of("Confirmation header",
            By.cssSelector("[data-test='complete-header']"), By.className("complete-header"));

    @Override
    public boolean isLoaded() {
        return currentUrl().contains("checkout");
    }

    @Step("Enter checkout details for {first} {last}")
    public CheckoutPage enterCustomerDetails(String first, String last, String zip) {
        type(firstName, first);
        type(lastName, last);
        type(postalCode, zip);
        return this;
    }

    @Step("Continue to the order summary")
    public CheckoutPage continueToSummary() {
        click(continueButton);
        // Either the summary loads or the form rejects the input. Both are valid outcomes, so this
        // waits for whichever arrives rather than assuming success and failing later with a
        // misleading "element not found".
        wait.until(d -> d.getCurrentUrl().contains("checkout-step-two") || isDisplayed(errorBanner));
        return this;
    }

    /** Short probe: by the time this is asked, {@link #continueToSummary()} has already settled. */
    public boolean hasValidationError() {
        return isDisplayed(errorBanner);
    }

    public String validationError() {
        return textOf(errorBanner);
    }

    public BigDecimal itemSubtotal() {
        return amountFrom(textOf(subtotalLabel));
    }

    public BigDecimal tax() {
        return amountFrom(textOf(taxLabel));
    }

    public BigDecimal orderTotal() {
        return amountFrom(textOf(totalLabel));
    }

    @Step("Place the order")
    public CheckoutPage finishOrder() {
        click(finishButton);
        return this;
    }

    public boolean isOrderConfirmed() {
        return waitUntilDisplayed(confirmationHeader);
    }

    public String confirmationMessage() {
        return textOf(confirmationHeader);
    }

    private static BigDecimal amountFrom(String label) {
        String digits = label.replaceAll("[^0-9.]", "");
        return new BigDecimal(digits);
    }
}
