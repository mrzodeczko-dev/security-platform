package com.app.infrastructure.configuration;

import com.app.infrastructure.configuration.properties.JwtProperties;
import com.app.infrastructure.configuration.properties.MfaCacheProperties;
import com.app.infrastructure.configuration.properties.UserServiceProperties;
import com.warrenstrange.googleauth.GoogleAuthenticator;
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
@EnableConfigurationProperties({
        UserServiceProperties.class,
        JwtProperties.class,
        MfaCacheProperties.class})
public class BeanConfiguration {

    @Bean
    public SecretKey secretKey(JwtProperties jwtProperties) {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.secret());
        return new SecretKeySpec(keyBytes, "HmacSHA512");
    }

    @Bean
    public GoogleAuthenticator googleAuthenticator() {
        return new GoogleAuthenticator();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public RestClientCustomizer restClientCustomizer(UserServiceProperties userServiceProperties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(userServiceProperties.connectTimeoutMs()))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(userServiceProperties.readTimeoutMs()));
        return builder -> builder.requestFactory(factory);
    }
}
