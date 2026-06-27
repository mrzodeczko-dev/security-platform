package com.rzodeczko.infrastructure.configuration;

import com.rzodeczko.application.port.in.GatewayPort;
import com.rzodeczko.application.port.out.ForwardingPort;
import com.rzodeczko.application.service.impl.GatewayServiceImpl;
import com.rzodeczko.domain.model.RoutingTable;
import com.rzodeczko.infrastructure.configuration.properties.GatewayProperties;
import com.rzodeczko.infrastructure.configuration.properties.JwtProperties;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    private static final int HS512_MIN_KEY_LENGTH = 64;

    @Bean
    public SecretKey secretKey(JwtProperties jwtProperties) {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.secret());
        if (keyBytes.length < HS512_MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "JWT secret must be at least %d bytes for HmacSHA512, got: %d"
                            .formatted(HS512_MIN_KEY_LENGTH, keyBytes.length));
        }
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

    @Bean
    @ConditionalOnProperty(prefix = "gateway.rate-limit", name = "enabled", havingValue = "true")
    public LettuceBasedProxyManager<String> rateLimitProxyManager(
            @Value("${spring.data.redis.host}") String redisHost,
            @Value("${spring.data.redis.port}") int redisPort,
            @Value("${spring.data.redis.password:}") String redisPassword) {

        var uriBuilder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort);

        if (redisPassword != null && !redisPassword.isBlank()) {
            uriBuilder.withPassword(redisPassword.toCharArray());
        }

        var client = RedisClient.create(uriBuilder.build());
        var connection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        return Bucket4jLettuce.casBasedBuilder(connection)
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofMinutes(5)))
                .build();
    }
}
