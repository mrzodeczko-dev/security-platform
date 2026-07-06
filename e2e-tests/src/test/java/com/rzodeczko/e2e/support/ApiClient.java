package com.rzodeczko.e2e.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Thin wrapper around REST-assured for hitting the API Gateway.
 * Provides convenience methods and manages the access token header.
 */
public final class ApiClient {

    private String accessToken;
    private String refreshTokenCookie;

    public ApiClient() {
    }

    // --- requests ---

    public RequestSpecification given() {
        var spec = RestAssured.given()
                .baseUri(E2ETestEnvironment.gatewayBaseUrl())
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON);

        if (accessToken != null) {
            spec.header("Authorization", "Bearer " + accessToken);
        }
        if (refreshTokenCookie != null) {
            spec.cookie("refresh-token", refreshTokenCookie);
        }
        return spec;
    }

    // --- auth helpers ---

    public Response register(String username, String email, String password) {
        return given()
                .body(Map.of(
                        "username", username,
                        "email", email,
                        "password", password,
                        "passwordConfirmation", password
                ))
                .when()
                .post("/users");
    }

    public Response login(String username, String password) {
        var response = given()
                .body(Map.of("username", username, "password", password))
                .when()
                .post("/auth/login");

        if (response.statusCode() == 201) {
            extractTokens(response);
        }
        return response;
    }

    public Response refresh() {
        var response = given().when().post("/auth/refresh");
        if (response.statusCode() == 201) {
            extractTokens(response);
        }
        return response;
    }

    public Response logout() {
        return logout(false);
    }

    public Response logout(boolean revokeAll) {
        var url = revokeAll ? "/auth/logout?revokeAll=true" : "/auth/logout";
        var response = given().when().post(url);
        if (response.statusCode() == 200) {
            accessToken = null;
            refreshTokenCookie = null;
        }
        return response;
    }

    // --- token management ---

    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void clearTokens() {
        this.accessToken = null;
        this.refreshTokenCookie = null;
    }

    private void extractTokens(Response response) {
        var at = response.jsonPath().getString("data.accessToken.accessToken");
        if (at == null) {
            at = response.jsonPath().getString("data.accessToken");
        }
        if (at != null) {
            this.accessToken = at;
        }

        var cookie = response.getDetailedCookie("refresh-token");
        if (cookie != null) {
            this.refreshTokenCookie = cookie.getValue();
        }
    }
}
