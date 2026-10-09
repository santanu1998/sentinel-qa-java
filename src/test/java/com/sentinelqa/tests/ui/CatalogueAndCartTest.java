package com.sentinelqa.tests.ui;

import com.sentinelqa.pages.CartPage;
import com.sentinelqa.pages.LoginPage;
import com.sentinelqa.pages.ProductsPage;
import com.sentinelqa.tests.BaseUiTest;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.qameta.allure.TmsLink;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Epic("Storefront")
@Feature("Catalogue and cart")
public class CatalogueAndCartTest extends BaseUiTest {

    /**
     * TestNG creates one instance per {@code <test>} and runs its methods in parallel on that same
     * instance, so a page object held in a field would be shared across worker threads and bound to
     * whichever driver constructed it. Each test builds its own, on its own thread.
     */
    private ProductsPage signIn() {
        return new LoginPage().open().loginAs("standard_user", "secret_sauce");
    }

    @Test(groups = {"smoke", "regression", "ui"},
            description = "Sorting by price ascending reorders the catalogue correctly")
    @Story("Catalogue sorting")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("STE-110")
    public void sortsByPriceAscending() {
        ProductsPage catalogue = signIn();
        catalogue.sortBy("Price (low to high)");

        assertTrue(catalogue.isSortedByPriceAscending(),
                "Prices were not ascending: " + catalogue.listedPrices());
    }

    @Test(groups = {"regression", "ui"},
            description = "Sorting by name descending reorders the catalogue correctly")
    @Story("Catalogue sorting")
    @Severity(SeverityLevel.MINOR)
    @TmsLink("STE-111")
    public void sortsByNameDescending() {
        ProductsPage catalogue = signIn();
        catalogue.sortBy("Name (Z to A)");

        assertTrue(catalogue.isSortedByNameDescending(),
                "Names were not in reverse order: " + catalogue.listedProducts());
    }

    @Test(groups = {"smoke", "regression", "ui"},
            description = "The cart badge tracks additions exactly")
    @Story("Add to cart")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-112")
    public void cartBadgeTracksAdditions() {
        ProductsPage catalogue = signIn();
        assertEquals(catalogue.cartCount(), 0, "Cart was not empty at the start of the test");

        catalogue.addToCart("Sauce Labs Backpack");
        assertEquals(catalogue.cartCount(), 1, "Badge did not register the first item");

        catalogue.addToCart("Sauce Labs Bike Light");
        assertEquals(catalogue.cartCount(), 2, "Badge did not register the second item");
    }

    @Test(groups = {"regression", "ui"},
            description = "The cart lists exactly the products that were added")
    @Story("Cart contents")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-113")
    public void cartListsAddedProducts() {
        ProductsPage catalogue = signIn();
        List<String> expected = List.of("Sauce Labs Backpack", "Sauce Labs Fleece Jacket");
        expected.forEach(catalogue::addToCart);

        CartPage cart = catalogue.openCart();

        assertTrue(cart.isLoaded(), "Cart page did not load");
        assertEquals(cart.itemCount(), expected.size(), "Cart item count mismatch");
        assertTrue(cart.itemNames().containsAll(expected),
                "Cart was missing products. Expected %s, found %s".formatted(expected, cart.itemNames()));
    }

    @Test(groups = {"regression", "ui"},
            description = "Removing an item updates both the cart and the badge")
    @Story("Cart contents")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("STE-114")
    public void removingItemUpdatesCartAndBadge() {
        ProductsPage catalogue = signIn();
        catalogue.addToCart("Sauce Labs Backpack").addToCart("Sauce Labs Bike Light");

        CartPage cart = catalogue.openCart().remove("Sauce Labs Backpack");

        assertEquals(cart.itemCount(), 1, "Removal did not update the cart list");
        assertFalse(cart.itemNames().contains("Sauce Labs Backpack"), "Removed product is still listed");
        assertEquals(cart.continueShopping().cartCount(), 1, "Badge did not follow the removal");
    }
}
