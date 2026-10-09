package com.sentinelqa.api;

import com.sentinelqa.api.model.Booking;
import com.sentinelqa.api.model.BookingResponse;
import com.sentinelqa.config.ConfigLoader;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Thin, typed client over the reservations API.
 *
 * <p>Tests assert; the client transports. Keeping the two apart is what stops assertion logic from
 * leaking into helpers and makes every endpoint reusable across functional, integration and
 * regression suites.</p>
 */
public final class BookingApiClient {

    private static final String BOOKING = "/booking";
    private static final String BOOKING_BY_ID = "/booking/{id}";

    @Step("POST /booking")
    public Response create(Booking booking) {
        return given().spec(ApiSpecFactory.request())
                .body(booking)
                .when().post(BOOKING)
                .then().extract().response();
    }

    public BookingResponse createAndExtract(Booking booking) {
        return create(booking).then().statusCode(200).extract().as(BookingResponse.class);
    }

    @Step("GET /booking/{id}")
    public Response getById(int id) {
        return given().spec(ApiSpecFactory.request())
                .pathParam("id", id)
                .when().get(BOOKING_BY_ID)
                .then().extract().response();
    }

    @Step("GET /booking (all ids)")
    public Response listIds() {
        return given().spec(ApiSpecFactory.request())
                .when().get(BOOKING)
                .then().extract().response();
    }

    @Step("GET /booking with filters")
    public Response search(Map<String, ?> filters) {
        return given().spec(ApiSpecFactory.request())
                .queryParams(filters)
                .when().get(BOOKING)
                .then().extract().response();
    }

    @Step("PUT /booking/{id}")
    public Response update(int id, Booking booking, String token) {
        return given().spec(ApiSpecFactory.authenticated(token))
                .pathParam("id", id)
                .body(booking)
                .when().put(BOOKING_BY_ID)
                .then().extract().response();
    }

    @Step("PATCH /booking/{id}")
    public Response partialUpdate(int id, Map<String, ?> changes, String token) {
        return given().spec(ApiSpecFactory.authenticated(token))
                .pathParam("id", id)
                .body(changes)
                .when().patch(BOOKING_BY_ID)
                .then().extract().response();
    }

    @Step("DELETE /booking/{id}")
    public Response delete(int id, String token) {
        return given().spec(ApiSpecFactory.authenticated(token))
                .pathParam("id", id)
                .when().delete(BOOKING_BY_ID)
                .then().extract().response();
    }

    /** Unauthenticated mutation, used by the negative/security suite. */
    @Step("DELETE /booking/{id} without a token")
    public Response deleteWithoutToken(int id) {
        return given().spec(ApiSpecFactory.request())
                .pathParam("id", id)
                .when().delete(BOOKING_BY_ID)
                .then().extract().response();
    }

    @Step("GET /ping (service health)")
    public Response health() {
        return given().spec(ApiSpecFactory.request())
                .when().get(ConfigLoader.get("api.health.path"))
                .then().extract().response();
    }
}
