package com.sentinelqa.tests.api;

import com.sentinelqa.api.AuthApiClient;
import com.sentinelqa.api.BookingApiClient;
import com.sentinelqa.api.model.Booking;
import com.sentinelqa.api.model.BookingResponse;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.qameta.allure.TmsLink;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Epic("Reservations API")
@Feature("Booking lifecycle")
public class BookingCrudApiTest {

    private final BookingApiClient bookings = new BookingApiClient();
    private final AuthApiClient auth = new AuthApiClient();
    private String token;

    @BeforeClass(alwaysRun = true)
    public void authenticate() {
        token = auth.sessionToken();
        assertNotNull(token, "Could not obtain a session token");
    }

    @Test(groups = {"smoke", "api"}, description = "The service answers its health probe")
    @Story("Service health")
    @Severity(SeverityLevel.BLOCKER)
    @TmsLink("STE-200")
    public void serviceIsHealthy() {
        assertEquals(bookings.health().statusCode(), 201, "Health probe did not return 201");
    }

    @Test(groups = {"smoke", "api", "regression"},
            description = "POST /booking persists every field it was sent")
    @Story("Create")
    @Severity(SeverityLevel.BLOCKER)
    @TmsLink("STE-201")
    @Description("TC-200 — creation round-trip, including the nested bookingdates object.")
    public void createsBookingWithAllFieldsPersisted() {
        Booking request = Booking.sample();

        BookingResponse created = bookings.createAndExtract(request);

        assertNotNull(created.getBookingid(), "Service did not return a booking id");
        assertEquals(created.getBooking().getFirstname(), request.getFirstname(), "firstname not persisted");
        assertEquals(created.getBooking().getLastname(), request.getLastname(), "lastname not persisted");
        assertEquals(created.getBooking().getTotalprice(), request.getTotalprice(), "totalprice not persisted");
        assertEquals(created.getBooking().getDepositpaid(), request.getDepositpaid(), "depositpaid not persisted");
        assertEquals(created.getBooking().getBookingdates().getCheckin(),
                request.getBookingdates().getCheckin(), "check-in date not persisted");
    }

    @Test(groups = {"api", "regression"}, dependsOnMethods = "createsBookingWithAllFieldsPersisted",
            description = "GET /booking/{id} returns the booking that was just created")
    @Story("Read")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-202")
    public void readsBackCreatedBooking() {
        BookingResponse created = bookings.createAndExtract(Booking.sample());

        Response read = bookings.getById(created.getBookingid());

        assertEquals(read.statusCode(), 200, "Read did not return 200");
        assertEquals(read.as(Booking.class), created.getBooking(),
                "Read-back booking does not match what was created");
    }

    @Test(groups = {"api", "regression"},
            description = "PUT /booking/{id} replaces the whole resource")
    @Story("Update")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-203")
    public void fullUpdateReplacesResource() {
        int id = bookings.createAndExtract(Booking.sample()).getBookingid();

        Booking replacement = Booking.builder()
                .firstname("Updated")
                .lastname("Record")
                .totalprice(27500)
                .depositpaid(false)
                .dates(LocalDate.now().plusDays(30), LocalDate.now().plusDays(34))
                .additionalneeds("Airport pickup")
                .build();

        Response response = bookings.update(id, replacement, token);

        assertEquals(response.statusCode(), 200, "Update did not return 200");
        Booking updated = response.as(Booking.class);
        assertEquals(updated.getFirstname(), "Updated", "firstname was not replaced");
        assertEquals(updated.getTotalprice(), Integer.valueOf(27500), "totalprice was not replaced");
        assertEquals(updated.getDepositpaid(), Boolean.FALSE, "depositpaid was not replaced");
    }

    @Test(groups = {"api", "regression"},
            description = "PATCH /booking/{id} changes only the supplied fields")
    @Story("Update")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("STE-204")
    @Description("TC-204 — the classic partial-update defect: untouched fields must survive.")
    public void partialUpdateLeavesOtherFieldsIntact() {
        Booking original = Booking.sample();
        int id = bookings.createAndExtract(original).getBookingid();

        Response response = bookings.partialUpdate(id, Map.of("firstname", "Patched"), token);

        assertEquals(response.statusCode(), 200, "Patch did not return 200");
        Booking patched = response.as(Booking.class);
        assertEquals(patched.getFirstname(), "Patched", "Patched field did not change");
        assertEquals(patched.getLastname(), original.getLastname(), "Untouched lastname was altered");
        assertEquals(patched.getTotalprice(), original.getTotalprice(), "Untouched totalprice was altered");
    }

    @Test(groups = {"api", "regression"},
            description = "DELETE /booking/{id} removes the booking")
    @Story("Delete")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-205")
    public void deleteRemovesBooking() {
        int id = bookings.createAndExtract(Booking.sample()).getBookingid();

        assertEquals(bookings.delete(id, token).statusCode(), 201, "Delete did not acknowledge");
        assertEquals(bookings.getById(id).statusCode(), 404, "Booking is still retrievable after deletion");
    }

    @Test(groups = {"api", "regression"},
            description = "GET /booking supports filtering by guest name")
    @Story("Search")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("STE-206")
    public void searchFiltersByGuestName() {
        String unique = "Guest" + System.currentTimeMillis();
        bookings.createAndExtract(Booking.builder()
                .firstname(unique)
                .lastname("Singha")
                .totalprice(9900)
                .depositpaid(true)
                .dates(LocalDate.now().plusDays(3), LocalDate.now().plusDays(5))
                .build());

        Response response = bookings.search(Map.of("firstname", unique));

        assertEquals(response.statusCode(), 200, "Search did not return 200");
        assertTrue(response.jsonPath().getList("bookingid").size() >= 1,
                "Search returned no match for a guest that was just created");
    }
}
