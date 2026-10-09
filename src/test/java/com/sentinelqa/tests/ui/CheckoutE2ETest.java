package com.sentinelqa.tests.ui;

import com.sentinelqa.pages.CheckoutPage;
import com.sentinelqa.pages.LoginPage;
import com.sentinelqa.pages.ProductsPage;
import com.sentinelqa.tests.BaseUiTest;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Epic("Storefront")
@Feature("Checkout")
public class CheckoutE2ETest extends BaseUiTest {

    /** Built per method, never held in a field: methods of one class run in parallel on one instance. */
    private ProductsPage signIn() {
        return new LoginPage().open().loginAs("standard_user", "secret_sauce");
    }

    @Test(groups = {"smoke", "regression", "ui", "e2e"},
            description = "Browse, add to cart, check out and confirm the order end to end")
    @Story("Place an order")
    @Severity(SeverityLevel.BLOCKER)
    @TmsLink("STE-120")
    @Description("TC-020 — the revenue path. If this fails, the release does not ship.")
    public void placesOrderEndToEnd() {
        CheckoutPage checkout = signIn()
                .addToCart("Sauce Labs Backpack")
                .addToCart("Sauce Labs Bike Light")
                .openCart()
                .proceedToCheckout()
                .enterCustomerDetails("Santanu", "Singha", "700001")
                .continueToSummary();

        assertTrue(checkout.isLoaded(), "Checkout summary did not load");
        assertEquals(checkout.finishOrder().isOrderConfirmed(), true, "Order was not confirmed");
        assertTrue(checkout.confirmationMessage().toLowerCase().contains("thank you"),
                "Unexpected confirmation message: " + checkout.confirmationMessage());
    }

    @Test(groups = {"regression", "ui", "e2e"},
            description = "Order total equals subtotal plus tax to the cent")
    @Story("Order totals")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-121")
    @Description("TC-021 — arithmetic on the summary screen, the defect class that reaches customers.")
    public void orderTotalEqualsSubtotalPlusTax() {
        CheckoutPage checkout = signIn()
                .addToCart("Sauce Labs Backpack")
                .addToCart("Sauce Labs Fleece Jacket")
                .openCart()
                .proceedToCheckout()
                .enterCustomerDetails("Santanu", "Singha", "700001")
                .continueToSummary();

        BigDecimal expected = checkout.itemSubtotal().add(checkout.tax()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal actual = checkout.orderTotal().setScale(2, RoundingMode.HALF_UP);

        assertEquals(actual, expected,
                "Order total %s does not equal subtotal %s plus tax %s"
                        .formatted(actual, checkout.itemSubtotal(), checkout.tax()));
    }

    @Test(groups = {"regression", "ui"},
            description = "Checkout refuses to advance when a mandatory field is blank")
    @Story("Checkout validation")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("STE-122")
    public void blankPostalCodeBlocksCheckout() {
        CheckoutPage checkout = signIn()
                .addToCart("Sauce Labs Backpack")
                .openCart()
                .proceedToCheckout()
                .enterCustomerDetails("Santanu", "Singha", "")
                .continueToSummary();

        assertTrue(checkout.hasValidationError(), "Blank postal code was accepted");
        assertTrue(checkout.validationError().contains("Postal Code"),
                "Validation message did not name the offending field: " + checkout.validationError());
    }
}
