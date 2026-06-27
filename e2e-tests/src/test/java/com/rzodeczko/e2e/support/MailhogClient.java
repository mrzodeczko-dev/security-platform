package com.rzodeczko.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Minimal MailHog REST API client for retrieving activation codes from
 * emails sent by user-service during registration.
 */
public final class MailhogClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private MailhogClient() {
    }

    /**
     * Search MailHog for the latest email to the given address and extract
     * a numeric activation code from its body.
     */
    public static Optional<String> getActivationCode(String recipientEmail) {
        try {
            var uri = URI.create(E2ETestEnvironment.mailhogApiUrl()
                    + "/api/v2/search?kind=to&query=" + recipientEmail);

            var req = HttpRequest.newBuilder(uri).GET().build();
            var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            var root = MAPPER.readTree(resp.body());
            var items = root.get("items");
            if (items == null || items.isEmpty()) {
                return Optional.empty();
            }

            // Latest email is first
            var body = extractBody(items.get(0));
            return extractCode(body);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Delete all messages from MailHog.
     */
    public static void deleteAll() {
        try {
            var uri = URI.create(E2ETestEnvironment.mailhogApiUrl() + "/api/v1/messages");
            var req = HttpRequest.newBuilder(uri).DELETE().build();
            HTTP.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    private static String extractBody(JsonNode message) {
        // MailHog stores the body in Content.Body (MIME)
        var content = message.path("Content");
        var body = content.path("Body").asText("");

        // If multipart, also check MIME parts
        if (body.isBlank()) {
            var parts = content.path("MIME").path("Parts");
            if (parts.isArray() && !parts.isEmpty()) {
                body = parts.get(0).path("Body").asText("");
            }
        }
        return body;
    }

    private static Optional<String> extractCode(String body) {
        // Look for a 6-digit activation code
        var matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(body);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }
}
