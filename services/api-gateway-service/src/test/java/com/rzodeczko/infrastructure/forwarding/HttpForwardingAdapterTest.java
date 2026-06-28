package com.rzodeczko.infrastructure.forwarding;

import com.rzodeczko.domain.exception.DownstreamUnavailableException;
import com.rzodeczko.domain.model.GatewayRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("HttpForwardingAdapter")
class HttpForwardingAdapterTest {

    private MockRestServiceServer server;
    private HttpForwardingAdapter adapter;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new HttpForwardingAdapter(builder);
    }

    @Test
    @DisplayName("forward GET — success 200")
    void forwardGetSuccess() {
        server.expect(requestTo("http://user-service:8080/api/users"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"users\":[]}", MediaType.APPLICATION_JSON));

        var request = new GatewayRequest(
                "/api/users", "GET", Map.of(), null, null
        );

        var response = adapter.forward("http://user-service:8080", request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body())).contains("users");
        server.verify();
    }

    @Test
    @DisplayName("forward POST with body")
    void forwardPostWithBody() {
        server.expect(requestTo("http://user-service:8080/api/users"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));

        var body = "{\"username\":\"john\"}".getBytes();
        var headers = new HashMap<String, List<String>>();
        headers.put("Content-Type", List.of("application/json"));
        var request = new GatewayRequest(
                "/api/users", "POST", headers, body, null
        );

        var response = adapter.forward("http://user-service:8080", request);

        assertThat(response.statusCode()).isEqualTo(200);
        server.verify();
    }

    @Test
    @DisplayName("forward with query string")
    void forwardWithQueryString() {
        server.expect(requestTo("http://user-service:8080/api/users?page=1&size=10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var request = new GatewayRequest(
                "/api/users", "GET", Map.of(), null, "page=1&size=10"
        );

        var response = adapter.forward("http://user-service:8080", request);

        assertThat(response.statusCode()).isEqualTo(200);
        server.verify();
    }

    @Test
    @DisplayName("downstream returns 4xx → response forwarded as-is")
    void downstream4xx() {
        server.expect(requestTo("http://user-service:8080/api/users/999"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"not found\"}"));

        var request = new GatewayRequest(
                "/api/users/999", "GET", Map.of(), null, null
        );

        var response = adapter.forward("http://user-service:8080", request);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(new String(response.body())).contains("not found");
        server.verify();
    }

    @Test
    @DisplayName("downstream returns 5xx → response forwarded as-is")
    void downstream5xx() {
        server.expect(requestTo("http://user-service:8080/api/users"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError().body("Internal error"));

        var request = new GatewayRequest(
                "/api/users", "GET", Map.of(), null, null
        );

        var response = adapter.forward("http://user-service:8080", request);

        assertThat(response.statusCode()).isEqualTo(500);
        server.verify();
    }
}
