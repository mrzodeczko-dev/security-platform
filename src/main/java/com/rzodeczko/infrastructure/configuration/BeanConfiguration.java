package com.rzodeczko.infrastructure.configuration;

import com.rzodeczko.application.port.out.ForwardingPort;
import com.rzodeczko.application.port.in.GatewayPort;
import com.rzodeczko.application.service.impl.GatewayServiceImpl;
import com.rzodeczko.domain.model.RoutingTable;
import com.rzodeczko.infrastructure.configuration.properties.GatewayProperties;
import com.rzodeczko.infrastructure.configuration.properties.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, GatewayProperties.class})
public class BeanConfiguration {
    @Bean
    public SecretKey secretKey(JwtProperties jwtProperties) {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.secret());
        return new SecretKeySpec(keyBytes, "HmacSHA512");
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestClientCustomizer restClientCustomizer(GatewayProperties gatewayProperties) {
        HttpClient httpClient = HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofMillis(gatewayProperties.forwarding().connectTimeoutMs()))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(gatewayProperties.forwarding().readTimeoutMs()));
        return builder -> builder.requestFactory(factory);
    }

    @Bean
    public RoutingTable routingTable(GatewayProperties gatewayProperties) {
        var routes = gatewayProperties
                .routes()
                .stream()
                .map(r -> new RoutingTable.Route(r.prefix(), r.target()))
                .toList();
        return new RoutingTable(routes);
    }

    @Bean
    public GatewayPort gatewayService(
            ForwardingPort forwardingPort,
            RoutingTable routingTable
    ) {
        return new GatewayServiceImpl(forwardingPort, routingTable);
    }
}
