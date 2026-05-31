package com.rzodeczko.infrastructure.security;

import com.rzodeczko.infrastructure.configuration.properties.InternalSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Map;

// Filtr zabezpieczający endpointy /internal/*.

// user-service nie ma spring-boot-starter-security (tylko spring-security-crypto).
// Zamiast pełnego Security filter chain implementujemy lekki OncePerRequestFilter
// który sprawdza X-Internal-Secret wyłącznie dla /internal/* ścieżek.

// DWIE WARSTWY OCHRONY /internal/*:
// 1. Docker network: port 8083 eksponowany przez expose (nie ports) →
//    dostępny tylko w Docker sieci km-network.
//    auth-service i api-gateway są w tej samej sieci.
//    Zewnętrzny klient nie ma dostępu do portu 8083.
// 2. X-Internal-Secret header: nawet jeśli ktoś jest w Docker sieci,
//    musi znać sekret (współdzielony z auth-service przez env var).

// TIMING ATTACK PREVENTION:
// Naiwne porównanie: if (provided.equals(expected)) →
// String.equals() zwraca false przy pierwszym różnym znaku → różny czas odpowiedzi
// zależny od długości wspólnego prefiksu → atakujący może odgadnąć sekret bajt po bajcie.
// MessageDigest.isEqual() → stały czas porównania niezależnie od treści → safe.

// Wyobraź sobie że masz zamek szyfrowy z kodem 8519.

// Jak działa String.equals()
// Porównuje znaki jeden po drugim i zatrzymuje się przy pierwszym błędzie.
// Próba "1234" → porównuje '1' z '8' → STOP, nie pasuje → odpowiedź po 0.001ms
// Próba "8000" → porównuje '8' z '8' → OK, '0' z '5' → STOP → odpowiedź po 0.002ms
// Próba "8500" → porównuje '8','5','0' → STOP przy trzecim → odpowiedź po 0.003ms
// Próba "8519" → porównuje wszystkie cztery → odpowiedź po 0.004ms
// Atakujący mierzy czas odpowiedzi. Im dłużej serwer odpowiada, tym więcej znaków jest poprawnych.
// Może odgadywać znak po znaku zamiast próbować wszystkich kombinacji. Zamiast milionów prób — kilkadziesiąt.

// Jak działa MessageDigest.isEqual()
// Zawsze porównuje wszystkie bajty do końca, niezależnie od tego czy pierwszy znak pasuje czy nie.
// Próba "1234" → sprawdza wszystkie 4 znaki → odpowiedź po 0.004ms
// Próba "8000" → sprawdza wszystkie 4 znaki → odpowiedź po 0.004ms
// Próba "8519" → sprawdza wszystkie 4 znaki → odpowiedź po 0.004ms
// Atakujący mierzy czas — zawsze taki sam. Nie dowiaduje się niczego.

// OncePerRequestFilter: Spring gwarantuje dokładnie jedno wywołanie na request
// (nie kilka przy forward/include w Spring MVC).
@Component
@RequiredArgsConstructor
@Slf4j
public class InternalRequestFilter extends OncePerRequestFilter {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final InternalSecurityProperties internalSecurityProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        var providedSecret = request.getHeader(INTERNAL_SECRET_HEADER);

        boolean secretValid = providedSecret != null && MessageDigest.isEqual(
                providedSecret.getBytes(),
                internalSecurityProperties.secret().getBytes()
        );

        if (!secretValid) {
            log.warn(
                    "Unauthorized /internal request from IP={}. Missing or invalid X-Internal-Secret header.",
                    request.getRemoteAddr()
            );
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Map.of("error", "Forbidden")));
            response.getWriter().flush();
            return;
        }

        filterChain.doFilter(request, response);
    }
}
