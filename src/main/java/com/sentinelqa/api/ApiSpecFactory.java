package com.sentinelqa.api;

import com.sentinelqa.config.ConfigLoader;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

import java.util.concurrent.TimeUnit;

/**
 * Centralised request/response specifications.
 *
 * <p>Every API test shares the same base URI, timeouts, content negotiation and Allure attachment
 * filter, so a request/response pair is always available on a failed result without a single
 * {@code System.out.println} in a test.</p>
 */
public final class ApiSpecFactory {

    private ApiSpecFactory() {
    }

    public static RequestSpecification request() {
        return new RequestSpecBuilder()
                .setBaseUri(ConfigLoader.get("api.base.url"))
                .setContentType(ContentType.JSON)
                // Explicit single-value Accept. REST Assured's ContentType.JSON expands to a
                // four-value header, which services that negotiate with res.format() answer with
                // 418 rather than JSON. Stating one type keeps the contract unambiguous.
                .addHeader("Accept", "application/json")
                .addFilter(new AllureRestAssured())
                .log(LogDetail.URI)
                .setConfig(io.restassured.RestAssured.config()
                        // Do not append ";charset=..." to Content-Type: strict gateways reject it.
                        .encoderConfig(io.restassured.config.EncoderConfig.encoderConfig()
                                .appendDefaultContentCharsetToContentTypeIfUndefined(false))
                        .httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                                .setParam("http.connection.timeout",
                                        (int) TimeUnit.SECONDS.toMillis(ConfigLoader.getInt("api.timeout.connect")))
                                .setParam("http.socket.timeout",
                                        (int) TimeUnit.SECONDS.toMillis(ConfigLoader.getInt("api.timeout.socket")))))
                .build();
    }

    public static RequestSpecification authenticated(String token) {
        return request().cookie("token", token);
    }

    public static ResponseSpecification expectOk() {
        return new ResponseSpecBuilder()
                .expectStatusCode(200)
                .expectResponseTime(org.hamcrest.Matchers.lessThan(
                        (long) ConfigLoader.getInt("api.sla.millis") + 1000L))
                .build();
    }

    public static ResponseSpecification expectStatus(int status) {
        return new ResponseSpecBuilder().expectStatusCode(status).build();
    }
}
