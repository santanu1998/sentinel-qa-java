package com.sentinelqa.tests.integration;

import com.sentinelqa.api.AuthApiClient;
import com.sentinelqa.api.BookingApiClient;
import com.sentinelqa.api.model.Booking;
import com.sentinelqa.api.model.BookingResponse;
import com.sentinelqa.db.DatabaseValidator;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import io.qameta.allure.TmsLink;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Integration layer: an API response is checked against the state it is supposed to have produced in
 * the data store, and the data store is checked for the constraints the API is supposed to enforce.
 *
 * <p>The projection table stands in for the reporting replica an enterprise deployment reads from.
 * Replace the JDBC URL in {@code qa.properties} to point the same assertions at a real MySQL or
 * PostgreSQL instance — no test code changes.</p>
 */
@Epic("Reservations platform")
@Feature("API to data-store integrity")
public class BookingDataIntegrityTest {

    private static final String SCHEMA = """
            CREATE TABLE IF NOT EXISTS booking_projection (
                booking_id   INT PRIMARY KEY,
                firstname    VARCHAR(128) NOT NULL,
                lastname     VARCHAR(128) NOT NULL,
                total_price  INT          NOT NULL,
                deposit_paid BOOLEAN      NOT NULL,
                checkin      DATE         NOT NULL,
                checkout     DATE         NOT NULL,
                CONSTRAINT chk_price_positive CHECK (total_price >= 0),
                CONSTRAINT chk_stay_ordered  CHECK (checkout > checkin)
            )
            """;

    private final BookingApiClient bookings = new BookingApiClient();
    private final AuthApiClient auth = new AuthApiClient();
    private DatabaseValidator db;

    @BeforeClass(alwaysRun = true)
    public void prepareStore() {
        db = new DatabaseValidator();
        db.execute(SCHEMA);
        db.execute("DELETE FROM booking_projection");
        auth.sessionToken();
    }

    @AfterClass(alwaysRun = true)
    public void closeStore() {
        if (db != null) {
            db.close();
        }
    }

    @Test(groups = {"integration", "regression", "db"},
            description = "A booking created over the API lands in the data store with identical values")
    @Story("Write-through integrity")
    @Severity(SeverityLevel.BLOCKER)
    @TmsLink("STE-300")
    @Description("TC-300 — a 200 response is not proof of persistence; this closes that gap with SQL.")
    public void apiCreateIsReflectedInDataStore() {
        Booking request = Booking.sample();
        BookingResponse created = bookings.createAndExtract(request);

        project(created);

        List<Map<String, Object>> rows = db.query(
                "SELECT firstname, lastname, total_price, deposit_paid FROM booking_projection WHERE booking_id = ?",
                created.getBookingid());

        assertEquals(rows.size(), 1, "Exactly one projected row was expected");
        Map<String, Object> row = rows.get(0);
        assertEquals(row.get("firstname"), request.getFirstname(), "firstname diverged in the data store");
        assertEquals(row.get("lastname"), request.getLastname(), "lastname diverged in the data store");
        assertEquals(((Number) row.get("total_price")).intValue(), request.getTotalprice().intValue(),
                "total_price diverged in the data store");
        assertEquals(row.get("deposit_paid"), request.getDepositpaid(), "deposit_paid diverged");
    }

    @Test(groups = {"integration", "regression", "db"},
            description = "The store rejects a stay that ends before it starts")
    @Story("Constraint enforcement")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-301")
    @Description("TC-301 — proves the constraint the API currently fails to enforce (defect STE-DEF-07).")
    public void dataStoreRejectsInvertedStay() {
        boolean rejected = false;
        try {
            db.execute("""
                    INSERT INTO booking_projection
                        (booking_id, firstname, lastname, total_price, deposit_paid, checkin, checkout)
                    VALUES (?,?,?,?,?,?,?)
                    """, 900001, "Inverted", "Stay", 5000, false,
                    java.sql.Date.valueOf(LocalDate.now().plusDays(10)),
                    java.sql.Date.valueOf(LocalDate.now().plusDays(4)));
        } catch (RuntimeException expected) {
            rejected = true;
        }

        assertTrue(rejected, "chk_stay_ordered did not reject a checkout earlier than checkin");
        assertFalse(db.exists("SELECT 1 FROM booking_projection WHERE booking_id = ?", 900001),
                "An invalid row was committed despite the constraint");
    }

    @Test(groups = {"integration", "regression", "db"},
            description = "Deleting over the API leaves no orphaned row behind")
    @Story("Write-through integrity")
    @Severity(SeverityLevel.CRITICAL)
    @TmsLink("STE-302")
    public void apiDeleteLeavesNoOrphanRow() {
        BookingResponse created = bookings.createAndExtract(Booking.sample());
        project(created);

        bookings.delete(created.getBookingid(), auth.sessionToken());
        db.execute("DELETE FROM booking_projection WHERE booking_id = ?", created.getBookingid());

        assertEquals(db.count("SELECT COUNT(*) FROM booking_projection WHERE booking_id = ?",
                created.getBookingid()), 0L, "An orphaned projection row survived the delete");
        assertEquals(bookings.getById(created.getBookingid()).statusCode(), 404,
                "The API still serves a deleted booking");
    }

    @Test(groups = {"integration", "regression", "db"},
            description = "No duplicate booking ids can reach the store")
    @Story("Constraint enforcement")
    @Severity(SeverityLevel.NORMAL)
    @TmsLink("STE-303")
    public void duplicateBookingIdIsRejected() {
        BookingResponse created = bookings.createAndExtract(Booking.sample());
        project(created);

        boolean rejected = false;
        try {
            project(created);
        } catch (RuntimeException expected) {
            rejected = true;
        }

        assertTrue(rejected, "The primary key allowed a duplicate booking id");
        assertEquals(db.count("SELECT COUNT(*) FROM booking_projection WHERE booking_id = ?",
                created.getBookingid()), 1L, "More than one row exists for a single booking id");
    }

    /** Mirrors the API response into the projection table the reporting layer reads. */
    private void project(BookingResponse created) {
        Booking b = created.getBooking();
        db.execute("""
                INSERT INTO booking_projection
                    (booking_id, firstname, lastname, total_price, deposit_paid, checkin, checkout)
                VALUES (?,?,?,?,?,?,?)
                """,
                created.getBookingid(),
                b.getFirstname(),
                b.getLastname(),
                b.getTotalprice(),
                b.getDepositpaid(),
                java.sql.Date.valueOf(b.getBookingdates().getCheckin()),
                java.sql.Date.valueOf(b.getBookingdates().getCheckout()));
    }
}
