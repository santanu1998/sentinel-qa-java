package com.sentinelqa.pages;

import com.sentinelqa.ai.SmartLocator;
import io.qameta.allure.Step;
import org.openqa.selenium.By;

import java.util.List;

/** Cart review step. */
public class CartPage extends BasePage {

    private final SmartLocator cartItems = SmartLocator.of("Cart item names",
            By.cssSelector("[data-test='inventory-item-name']"), By.className("inventory_item_name"));

    private final SmartLocator checkoutButton = SmartLocator.of("Checkout button",
            By.cssSelector("[data-test='checkout']"), By.id("checkout"));

    private final SmartLocator continueShopping = SmartLocator.of("Continue shopping",
            By.cssSelector("[data-test='continue-shopping']"), By.id("continue-shopping"));

    @Override
    public boolean isLoaded() {
        return currentUrl().contains("cart") && waitUntilDisplayed(checkoutButton);
    }

    public List<String> itemNames() {
        return textsOf(cartItems);
    }

    public int itemCount() {
        return itemNames().size();
    }

    @Step("Remove '{product}' from the cart")
    public CartPage remove(String product) {
        String slug = product.toLowerCase().replace(' ', '-').replaceAll("[^a-z0-9-]", "");
        click(SmartLocator.of("Remove: " + product,
                By.cssSelector("[data-test='remove-" + slug + "']"),
                By.xpath("//div[text()='" + product + "']/ancestor::div[contains(@class,'cart_item')]"
                        + "//button[contains(text(),'Remove')]")));
        return this;
    }

    @Step("Proceed to checkout")
    public CheckoutPage proceedToCheckout() {
        click(checkoutButton);
        wait.until(d -> d.getCurrentUrl().contains("checkout-step-one")
                || d.findElements(By.id("first-name")).size() > 0
                || d.findElements(By.id("continue")).size() > 0);
        return new CheckoutPage();
    }

    @Step("Return to the catalogue")
    public ProductsPage continueShopping() {
        click(continueShopping);
        waitForUrlContains("inventory.html");
        return new ProductsPage();
    }
}
