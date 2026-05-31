package com.rzodeczko.infrastructure.configuration;

import com.rzodeczko.infrastructure.configuration.properties.InternalSecurityProperties;
import com.rzodeczko.infrastructure.configuration.properties.MfaProperties;
import com.rzodeczko.infrastructure.configuration.properties.PasswordEncoderProperties;
import com.rzodeczko.infrastructure.configuration.properties.UserActivationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        InternalSecurityProperties.class,
        PasswordEncoderProperties.class,
        UserActivationProperties.class,
        MfaProperties.class
})
public class BeanConfiguration {
}
