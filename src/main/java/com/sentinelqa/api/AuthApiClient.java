package com.sentinelqa.api;

import com.sentinelqa.config.ConfigLoader;
import io.qameta.allure.Step;
import io.restassured.response.Response;

import java.util.Map;

import static io.restassured.RestAssured.given;

/** Issues and caches the session token used by every mutating API call. */
public final class AuthApiClient {

    private static final String AUTH = "/auth";
    private static volatile String cachedToken;

    @Step("POST /auth")
    public Response requestToken(String username, String password) {
        return given().spec(ApiSpecFactory.request())
                .body(Map.of("username", username, "password", password))
                .when().post(AUTH)
                .then().extract().response();
    }

    public String token(String username, String password) {
        Response response = requestToken(username, password);
        String token = response.jsonPath().getString("token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "Authentication failed (HTTP " + response.statusCode() + "): " + response.asString());
        }
        return token;
    }

    /**
     * One token per JVM run. Re-issuing a token per test adds latency and, on rate-limited
     * environments, is itself a source of false failures.
     */
    public String sessionToken() {
        if (cachedToken == null) {
            synchronized (AuthApiClient.class) {
                if (cachedToken == null) {
                    cachedToken = token(ConfigLoader.get("api.admin.username"),
                            ConfigLoader.get("api.admin.password"));
                }
            }
        }
        return cachedToken;
    }

    public static void invalidateCache() {
        cachedToken = null;
    }
}
