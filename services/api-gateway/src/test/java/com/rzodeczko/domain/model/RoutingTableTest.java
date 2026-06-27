package com.rzodeczko.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingTableTest {

    @Test
    @DisplayName("resolves target for matching prefix")
    void resolvesTargetForMatchingPrefix() {
        var table = new RoutingTable(List.of(
                new RoutingTable.Route("/auth", "http://auth:8084"),
                new RoutingTable.Route("/users", "http://users:8083")
        ));

        assertThat(table.resolveTarget("/auth/login")).contains("http://auth:8084");
        assertThat(table.resolveTarget("/users/42")).contains("http://users:8083");
    }

    @Test
    @DisplayName("returns empty for unknown prefix")
    void returnsEmptyForUnknownPrefix() {
        var table = new RoutingTable(List.of(
                new RoutingTable.Route("/auth", "http://auth:8084")
        ));

        assertThat(table.resolveTarget("/orders/1")).isEmpty();
    }

    @Test
    @DisplayName("matches first route when prefixes overlap")
    void matchesFirstRouteOnOverlap() {
        var table = new RoutingTable(List.of(
                new RoutingTable.Route("/auth", "http://auth-general:8080"),
                new RoutingTable.Route("/auth/admin", "http://auth-admin:8081")
        ));

        // /auth/admin/... matches /auth first because it comes first in the list
        assertThat(table.resolveTarget("/auth/admin/users"))
                .contains("http://auth-general:8080");
    }

    @Test
    @DisplayName("exact prefix match works")
    void exactPrefixMatchWorks() {
        var table = new RoutingTable(List.of(
                new RoutingTable.Route("/auth", "http://auth:8084")
        ));

        assertThat(table.resolveTarget("/auth")).contains("http://auth:8084");
    }

    @Test
    @DisplayName("empty routing table returns empty")
    void emptyTableReturnsEmpty() {
        var table = new RoutingTable(List.of());

        assertThat(table.resolveTarget("/anything")).isEmpty();
    }
}
