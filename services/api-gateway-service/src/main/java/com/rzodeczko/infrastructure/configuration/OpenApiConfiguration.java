package com.rzodeczko.infrastructure.configuration;

import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Merges downstream OpenAPI specs into a single gateway spec.
 **/
@Slf4j
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
class OpenApiConfiguration {

    private static final String SPECS_PATTERN = "classpath:openapi/*.json";

    @Bean
    OpenAPI gatewayOpenAPI() {
        var merged = new OpenAPI()
                .info(new Info()
                        .title("Security API Gateway")
                        .version("1.0.0")
                        .description("API Gateway proxying requests to Auth Service and User Service"));

        var resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] specs = resolver.getResources(SPECS_PATTERN);
            for (Resource spec : specs) {
                merge(merged, spec);
            }
            log.info("Loaded {} downstream OpenAPI spec(s)", specs.length);
        } catch (IOException e) {
            log.warn("Failed to load downstream OpenAPI specs from {}: {}", SPECS_PATTERN, e.getMessage());
        }

        return merged;
    }

    private void merge(OpenAPI target, Resource specResource) throws IOException {
        String json = specResource.getContentAsString(StandardCharsets.UTF_8);
        OpenAPI source = Json31.mapper().readValue(json, OpenAPI.class);

        String name = specResource.getFilename();
        log.info("Merging OpenAPI spec: {}", name);


        if (source.getPaths() != null) {
            source.getPaths().forEach(target::path);
        }

        if (source.getComponents() != null && source.getComponents().getSchemas() != null) {
            if (target.getComponents() == null) {
                target.components(new io.swagger.v3.oas.models.Components());
            }
            source.getComponents().getSchemas().forEach((schemaName, schema) ->
                    target.getComponents().addSchemas(schemaName, schema));
        }
    }
}
