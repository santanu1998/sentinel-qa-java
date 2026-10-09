package com.sentinelqa.tests.ui;

import com.sentinelqa.pages.LoginPage;
import com.sentinelqa.pages.ProductsPage;
import com.sentinelqa.tests.BaseUiTest;
import com.sentinelqa.utils.JsonDataReader;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.qameta.allure.TmsLink;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Epic("Storefront")
@Feature("Authentication")
public class LoginTest extends BaseUiTest {

    @DataProvider(name = "invalidCredentials")
    public Object[][] invalidCredentials() {
        return JsonDataReader.asDataProvider("testdata/invalid-logins.json");
    }

    @Test(groups = {"smoke", "regression", "ui"},
            description = "A valid user reaches the catalogue after sign-in")
    @Story("Valid sign-in")
    @Severity(SeverityLevel.BLOCKER)
    @TmsLink("STE-101")
    @Description("TC-001 — the happy path that gates every other UI test in the suite.")
    public void validUserReachesCatalogue() {
        ProductsPage catalogue = new LoginPage().open().loginAs("standard_user", "secret_sauce");

        assertTrue(catalogue.isLoaded(), "Catalogue did not load after a valid sign-in");
        assertEquals(catalogue.heading(), "Products", "Unexpected catalogue heading");
        assertTrue(catalogue.listedProducts().size() >= 6,
                "Catalogue returned fewer products than the seeded minimum");
    }

    @Test(groups = {"regression", "ui"}, dataProvider = "invalidCredentials",
            description = "Rejected credentials surface a specific, non-leaking error")
    @Story("Rejected sign-in")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-102")
    @Description("TC-002 — boundary and negative coverage for the login form.")
    public void rejectedCredentialsShowExpectedError(Map<String, Object> row) {
        LoginPage login = new LoginPage().open()
                .loginExpectingFailure(String.valueOf(row.get("username")),
                        String.valueOf(row.get("password")));

        assertTrue(login.hasError(), "No error banner for case: " + row.get("case"));
        assertTrue(login.errorMessage().contains(String.valueOf(row.get("expectedMessage"))),
                "Case '%s': expected message containing '%s' but found '%s'"
                        .formatted(row.get("case"), row.get("expectedMessage"), login.errorMessage()));
    }

    @Test(groups = {"regression", "ui"},
            description = "A locked-out account is refused with the correct message")
    @Story("Account lockout")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-103")
    public void lockedOutUserIsRefused() {
        LoginPage login = new LoginPage().open()
                .loginExpectingFailure("locked_out_user", "secret_sauce");

        assertTrue(login.errorMessage().contains("locked out"),
                "Locked account was not refused with the expected message: " + login.errorMessage());
    }

    @Test(groups = {"regression", "ui"},
            description = "The session is not established when sign-in fails")
    @Story("Rejected sign-in")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("STE-104")
    public void failedSignInDoesNotCreateSession() {
        LoginPage login = new LoginPage().open().loginExpectingFailure("ghost_user", "wrong_password");

        assertTrue(login.currentUrl().endsWith("/"),
                "Browser navigated away from the login page despite a rejected sign-in");
    }
}
