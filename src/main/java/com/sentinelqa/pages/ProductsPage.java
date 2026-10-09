package com.sentinelqa.pages;

import com.sentinelqa.ai.SmartLocator;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Product catalogue: listing, sorting and add-to-cart behaviour. */
public class ProductsPage extends BasePage {

    private final SmartLocator pageTitle = SmartLocator.of("Catalogue heading",
            By.cssSelector("[data-test='title']"), By.className("title"));

    private final SmartLocator productNames = SmartLocator.of("Product names",
            By.cssSelector("[data-test='inventory-item-name']"), By.className("inventory_item_name"));

    private final SmartLocator productPrices = SmartLocator.of("Product prices",
            By.cssSelector("[data-test='inventory-item-price']"), By.className("inventory_item_price"));

    private final SmartLocator sortDropdown = SmartLocator.of("Sort selector",
            By.cssSelector("[data-test='product-sort-container']"), By.className("product_sort_container"));

    private final SmartLocator cartBadge = SmartLocator.of("Cart badge",
            By.cssSelector("[data-test='shopping-cart-badge']"), By.className("shopping_cart_badge"));

    private final SmartLocator cartLink = SmartLocator.of("Cart link",
            By.cssSelector("[data-test='shopping-cart-link']"), By.className("shopping_cart_link"));

    @Override
    public boolean isLoaded() {
        return currentUrl().contains("inventory") && waitUntilDisplayed(pageTitle);
    }

    public String heading() {
        return textOf(pageTitle);
    }

    public List<String> listedProducts() {
        return textsOf(productNames);
    }

    public List<BigDecimal> listedPrices() {
        return textsOf(productPrices).stream()
                .map(p -> p.replace("$", "").trim())
                .map(BigDecimal::new)
                .toList();
    }

    @Step("Sort catalogue by {visibleOption}")
    public ProductsPage sortBy(String visibleOption) {
        new Select(sortDropdown.resolve(driver)).selectByVisibleText(visibleOption);
        return this;
    }

    public boolean isSortedByPriceAscending() {
        List<BigDecimal> prices = listedPrices();
        return prices.equals(prices.stream().sorted().toList());
    }

    public boolean isSortedByNameDescending() {
        List<String> names = listedProducts();
        return names.equals(names.stream().sorted(Comparator.reverseOrder()).toList());
    }

    @Step("Add '{product}' to the cart")
    public ProductsPage addToCart(String product) {
        String slug = product.toLowerCase().replace(' ', '-').replaceAll("[^a-z0-9-]", "");
        SmartLocator addButton = SmartLocator.of("Add to cart: " + product,
                By.cssSelector("[data-test='add-to-cart-" + slug + "']"),
                By.xpath("//div[normalize-space(.)='" + product + "']/ancestor::div[contains(@class,'inventory_item')]"
                        + "//button[contains(.,'Add to cart')]"),
                By.xpath("//button[contains(.,'Add to cart') and ancestor::*[contains(@class,'inventory_item')][.//*[normalize-space(.)='" + product + "']]]"),
                By.xpath("//button[contains(@id,'add-to-cart') and ancestor::*[contains(@class,'inventory_item')][.//*[normalize-space(.)='" + product + "']]]"));
        click(addButton);

        SmartLocator removeButton = SmartLocator.of("Remove: " + product,
                By.cssSelector("[data-test='remove-" + slug + "']"),
                By.xpath("//div[normalize-space(.)='" + product + "']/ancestor::div[contains(@class,'inventory_item')]"
                        + "//button[contains(.,'Remove')]"),
                By.xpath("//button[contains(.,'Remove') and ancestor::*[contains(@class,'inventory_item')][.//*[normalize-space(.)='" + product + "']]]"),
                By.xpath("//button[contains(@id,'remove-') and ancestor::*[contains(@class,'inventory_item')][.//*[normalize-space(.)='" + product + "']]]"));
        wait.until(d -> removeButton.isPresent(d) || cartCount() > 0);
        return this;
    }

    public int cartCount() {
        if (!isDisplayed(cartBadge)) {
            return 0;
        }
        return Integer.parseInt(textOf(cartBadge));
    }

    @Step("Open the cart")
    public CartPage openCart() {
        click(cartLink);
        waitForUrlContains("cart.html");
        return new CartPage();
    }
}
