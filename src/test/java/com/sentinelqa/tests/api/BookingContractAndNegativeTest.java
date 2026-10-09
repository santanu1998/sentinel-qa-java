package com.sentinelqa.tests.api;

import com.sentinelqa.api.ApiSpecFactory;
import com.sentinelqa.api.AuthApiClient;
import com.sentinelqa.api.BookingApiClient;
import com.sentinelqa.api.model.Booking;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.qameta.allure.TmsLink;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

/**
 * Contract and negative coverage. These are the tests that catch the defects integration tests
 * assume away: wrong status codes, silent schema drift and unauthenticated mutation.
 */
@Epic("Reservations API")
@Feature("Contract and negative paths")
public class BookingContractAndNegativeTest {

    private final BookingApiClient bookings = new BookingApiClient();
    private final AuthApiClient auth = new AuthApiClient();

    @Test(groups = {"smoke", "api"}, description = "POST /booking matches the agreed JSON schema")
    @Story("Schema contract")
    @Severity(SeverityLevel.BLOCKER)
    @TmsLink("STE-210")
    @Description("TC-210 — schema validation catches field renames and type changes before consumers do.")
    public void createResponseMatchesSchema() {
        given().spec(ApiSpecFactory.request())
                .body(Booking.sample())
                .when().post("/booking")
                .then().spec(ApiSpecFactory.expectOk())
                .body(matchesJsonSchemaInClasspath("schemas/booking-create-schema.json"));
    }

    @Test(groups = {"api", "regression"}, description = "POST /auth matches the token schema")
    @Story("Schema contract")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-211")
    public void authResponseMatchesSchema() {
        given().spec(ApiSpecFactory.request())
                .body(Map.of("username", "admin", "password", "password123"))
                .when().post("/auth")
                .then().statusCode(200)
                .body(matchesJsonSchemaInClasspath("schemas/auth-token-schema.json"));
    }

    @Test(groups = {"api", "regression"},
            description = "A mutation without a token is refused")
    @Story("Authorisation")
    @Severity(SeverityLevel.BLOCKER)
    @TmsLink("STE-212")
    @Description("TC-212 — an unauthenticated DELETE must never succeed.")
    public void unauthenticatedDeleteIsRefused() {
        int id = bookings.createAndExtract(Booking.sample()).getBookingid();

        Response response = bookings.deleteWithoutToken(id);

        assertEquals(response.statusCode(), 403,
                "Unauthenticated delete returned " + response.statusCode() + " instead of 403");
        assertEquals(bookings.getById(id).statusCode(), 200,
                "The booking was deleted despite the request being unauthorised");
    }

    @Test(groups = {"api", "regression"},
            description = "An invalid credential pair yields no token")
    @Story("Authorisation")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-213")
    public void invalidCredentialsYieldNoToken() {
        Response response = auth.requestToken("admin", "wrong-password");

        assertEquals(response.statusCode(), 200, "Auth endpoint changed its status contract");
        assertEquals(response.jsonPath().getString("token"), null,
                "A token was issued for invalid credentials");
        assertTrue(response.jsonPath().getString("reason").toLowerCase().contains("bad credentials"),
                "Rejection reason was not returned");
    }

    @Test(groups = {"api", "regression"},
            description = "An unknown booking id returns 404, not 200 with an empty body")
    @Story("Error handling")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("STE-214")
    public void unknownBookingReturns404() {
        assertEquals(bookings.getById(99_999_999).statusCode(), 404,
                "Unknown id did not produce a 404");
    }

    @Test(groups = {"api", "regression"},
            description = "A malformed payload is rejected rather than silently stored")
    @Story("Input validation")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-215")
    @Description("TC-215 — a missing mandatory field must not create a half-populated record.")
    public void malformedPayloadIsRejected() {
        Response response = given().spec(ApiSpecFactory.request())
                .body(Map.of("lastname", "OnlyLastName"))
                .when().post("/booking")
                .then().extract().response();

        assertNotEquals(response.statusCode(), 200,
                "A payload missing mandatory fields was accepted with 200");
        assertTrue(response.statusCode() >= 400 && response.statusCode() < 600,
                "Expected a 4xx/5xx for a malformed payload but received " + response.statusCode());
    }

    @Test(groups = {"api", "regression"},
            description = "Response time stays inside the agreed SLA")
    @Story("Performance budget")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("STE-216")
    public void readStaysWithinSla() {
        int id = bookings.createAndExtract(Booking.sample()).getBookingid();

        bookings.getById(id).then().spec(ApiSpecFactory.expectOk());
    }

    @Test(groups = {"api", "known-defects"},
            description = "A check-out date before check-in is not accepted as valid data")
    @Story("Input validation")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("STE-217")
    public void invertedStayDatesAreFlagged() {
        Booking inverted = Booking.builder()
                .firstname("Inverted")
                .lastname("Dates")
                .totalprice(5000)
                .depositpaid(false)
                .dates(LocalDate.now().plusDays(10), LocalDate.now().plusDays(4))
                .build();

        Response response = bookings.create(inverted);

        // Documented product gap: the service stores the record instead of rejecting it.
        // The assertion states the correct behaviour so the linked defect stays visible in the run.
        assertNotEquals(response.statusCode(), 200,
                "Defect STE-DEF-07: a stay ending before it starts was accepted by POST /booking");
    }
}
