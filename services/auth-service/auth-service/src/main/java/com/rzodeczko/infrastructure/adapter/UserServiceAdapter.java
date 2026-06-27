package com.rzodeczko.infrastructure.adapter;


import com.rzodeczko.application.port.UserVerificationPort;
import com.rzodeczko.domain.exception.UserServiceUnavailableException;
import com.rzodeczko.domain.model.MfaData;
import com.rzodeczko.domain.model.UserCredentials;
import com.rzodeczko.infrastructure.adapter.dto.GetMfaDataRequestDto;
import com.rzodeczko.infrastructure.adapter.dto.MfaDataResponseDto;
import com.rzodeczko.infrastructure.adapter.dto.VerifyCredentialsRequestDto;
import com.rzodeczko.infrastructure.adapter.dto.VerifyCredentialsResponseDto;
import com.rzodeczko.infrastructure.configuration.properties.UserServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@Slf4j
public class UserServiceAdapter implements UserVerificationPort {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final RestClient restClient;
    private final UserServiceProperties properties;

    public UserServiceAdapter(RestClient.Builder restClientBuilder, UserServiceProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.url())
                .build();
    }

    @Override
    public UserCredentials verifyCredentials(String username, String password) {
        log.debug("Calling user-service /internal/users/credentials for username={}", username);
        try {
            var response = restClient
                    .post()
                    .uri("/internal/users/credentials")
                    .header(INTERNAL_SECRET_HEADER, properties.internalSecret())
                    .body(new VerifyCredentialsRequestDto(username, password))
                    .retrieve()
                    .body(VerifyCredentialsResponseDto.class);

            if (response == null) {
                throw new UserServiceUnavailableException("Empty response from /internal/users/credentials");
            }

            return new UserCredentials(
                    response.userId(),
                    response.username(),
                    response.role(),
                    response.mfaRequired()
            );
        } catch (RestClientResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new UserServiceUnavailableException(e.getMessage());
        }
    }

    @Override
    public MfaData getMfaData(String username) {
        log.debug("Calling user-service /internal/users/mfa for username={}", username);
        try {
            var response = restClient
                    .post()
                    .uri("/internal/users/mfa")
                    .header(INTERNAL_SECRET_HEADER, properties.internalSecret())
                    .body(new GetMfaDataRequestDto(username))
                    .retrieve()
                    .body(MfaDataResponseDto.class);

            if (response == null) {
                throw new UserServiceUnavailableException("Empty response from /internal/users/mfa");
            }

            return new MfaData(
                    response.userId(),
                    response.username(),
                    response.role(),
                    response.mfaSecret()
            );
        } catch (RestClientResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new UserServiceUnavailableException(e.getMessage());
        }
    }
}
