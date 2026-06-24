# Security API Gateway

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Redis](https://img.shields.io/badge/Redis-8.2-DC382D.svg?logo=redis&logoColor=white)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![CI](https://github.com/mrzodeczko-dev/security-api-gateway/actions/workflows/ci.yml/badge.svg)](https://github.com/mrzodeczko-dev/security-api-gateway/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

<a id="overview"></a>
## Overview
[Back to Table of Contents](#toc)

Security API Gateway is a custom-built reverse proxy that sits in front of the microservice ecosystem (Auth Service, User Service) and handles JWT authentication, role-based access control, per-client rate limiting, and request forwarding. Built on Hexagonal Architecture with a clean separation between domain, application, and infrastructure layers. The gateway verifies JWT tokens, enriches downstream requests with trusted `X-User-*` headers, strips unsafe response headers, and protects backend services with a distributed Circuit Breaker and Redis-backed rate limiter (Bucket4j).

<a id="toc"></a>
## Table of Contents
- [Overview](#overview)
- [How It Works](#how-it-works)
- [API Endpoints](#api-endpoints)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [Common Issues](#common-issues)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Testing Strategy](#testing-strategy)
- [Repository Structure](#repository-structure)
- [Contact](#contact)

---

<a id="how-it-works"></a>
## How It Works
[Back to Table of Contents](#toc)

### Request Lifecycle

1. Client sends an HTTP request to the gateway (port `8085`)
2. `AccessLogFilter` starts a timer and wraps the entire filter chain for structured access logging
3. `JwtAuthorizationFilter` checks for a `Bearer` token in the `Authorization` header. If present, it verifies the token via `JwtTokenVerificationAdapter` (HMAC-SHA512), validates the `type` claim is `access`, and populates the `SecurityContext` with userId, username, and role
4. `RateLimitFilter` resolves a rate-limit key (authenticated: `rl:user:{userId}`, anonymous: `rl:ip:{clientIp}`) and consumes a token from a Redis-backed Bucket4j bucket. If the bucket is empty, the request is rejected with `429 Too Many Requests` and a `Retry-After` header
5. Spring Security enforces path-based authorization rules: public paths (`permitAll`), admin paths (`ROLE_ADMIN`), user paths (`ROLE_USER` or `ROLE_ADMIN`), everything else requires authentication
6. `GatewayController` reads the request body (with a configurable size limit), extracts user data from `SecurityContext`, and delegates to `GatewayServiceImpl`
7. `GatewayServiceImpl` resolves the target service via `RoutingTable` (prefix-based matching), filters request headers through a whitelist (`content-type`, `accept`, `cache-control`), adds trusted `X-User-Id`, `X-User-Name`, `X-User-Role` headers, and attaches/generates an `X-Request-Id` for distributed tracing
8. `HttpForwardingAdapter` normalizes the path (preventing path traversal), forwards the request via `RestClient` with a per-target `CircuitBreaker` (Resilience4j), and returns the downstream response
9. The gateway strips hop-by-hop and server-revealing headers (`server`, `x-powered-by`, `transfer-encoding`, etc.) from the response and returns it to the client
10. `AccessLogFilter` logs the final status, latency, userId, and requestId

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant AL as AccessLogFilter
    participant JWT as JwtAuthorizationFilter
    participant RL as RateLimitFilter
    participant SS as Spring Security
    participant GC as GatewayController
    participant GS as GatewayService
    participant FW as HttpForwardingAdapter
    participant DS as Downstream Service
    participant R as Redis

    C->>AL: HTTP Request
    AL->>JWT: doFilter
    JWT->>JWT: verify Bearer token (HMAC-SHA512)
    JWT->>SS: set SecurityContext (userId, role)
    SS->>RL: doFilter
    RL->>R: tryConsume (Bucket4j)
    R-->>RL: remaining tokens
    alt rate limit exceeded
        RL-->>C: 429 Too Many Requests + Retry-After
    end
    RL->>SS: doFilter
    SS->>SS: authorize (public / user / admin)
    SS->>GC: handle()
    GC->>GS: GatewayRequest + userId + role
    GS->>GS: resolve route, filter headers, add X-User-*
    GS->>FW: forward (CircuitBreaker wrapped)
    FW->>DS: RestClient call
    DS-->>FW: response
    FW-->>GS: GatewayResponse
    GS->>GS: strip unsafe response headers
    GS-->>GC: GatewayResponse
    GC-->>AL: ResponseEntity
    AL->>AL: log method, path, status, duration, userId, requestId
    AL-->>C: HTTP Response
```

---

<a id="api-endpoints"></a>
## API Endpoints
[Back to Table of Contents](#toc)

The gateway proxies all requests to downstream services based on path prefix. It also exposes its own health check.

**Base URL:** `http://localhost:8085`

### Gateway Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `GET` | `/` | public | Gateway health check |
| `GET` | `/actuator/health` | public | Spring Actuator health |

### Auth Service (`/auth/*` -> Auth Service)

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/auth/login` | public | Authenticate user |
| `POST` | `/auth/refresh` | public | Refresh access token |
| `POST` | `/auth/mfa` | public | MFA verification |
| `POST` | `/auth/logout` | public | Logout (invalidate refresh token) |

### User Service (`/users/*` -> User Service)

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/users` | public | Register new user |
| `POST` | `/users/code` | public | Request activation code |
| `POST` | `/users/activation` | public | Activate account |
| `POST` | `/users/password/permission` | public | Request password reset |
| `POST` | `/users/password/reset` | public | Reset password |
| `PUT` | `/users/*/role` | ADMIN | Change user role |
| `POST` | `/users/*/mfa` | USER, ADMIN | Enable/configure MFA |

### Error Responses

| Status | Meaning | Example |
|--------|---------|---------|
| `401` | Missing or invalid JWT | `{"error": "Unauthorized"}` |
| `403` | Authenticated but lacking role | `{"error": "Forbidden"}` |
| `404` | No route matches the path | `{"error": "No route configured for path: /unknown"}` |
| `413` | Request body exceeds size limit | `{"error": "..."}` |
| `429` | Rate limit exceeded | `{"error": "Too many requests"}` + `Retry-After` header |
| `502` | Downstream service unreachable | `{"error": "Service temporarily unavailable"}` |
| `503` | Circuit breaker open | `{"error": "Service temporarily unavailable"}` |

### cURL Examples

```bash
# Login (public)
curl -X POST http://localhost:8085/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"secret"}'

# Access protected endpoint with JWT
curl http://localhost:8085/users/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."

# Change user role (admin only)
curl -X PUT http://localhost:8085/users/123/role \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"role":"ROLE_ADMIN"}'
```

---

<a id="getting-started"></a>
## Getting Started
[Back to Table of Contents](#toc)

### Prerequisites

- Docker and Docker Compose v2+
- Java 25+ and Maven 3.9+ (for local builds only)
- Auth Service and User Service images available (see `docker-compose.yml`)

### Environment Configuration

Copy the example and fill in secrets:

```bash
cp .env.example .env
```

See `.env.example` for all required variables with descriptions.

### Start the Stack

```bash
docker compose up -d --build
```

Verify: `curl http://localhost:8085/actuator/health` -> `{"status":"UP"}`

---

<a id="environment-variables"></a>
## Environment Variables
[Back to Table of Contents](#toc)

### API Gateway

| Variable | Required | Description | Default |
|----------|----------|-------------|---------|
| `JWT_SECRET` | yes | Base64-encoded HMAC-SHA512 key (min 64 bytes) | - |
| `AUTH_SERVICE_URL` | yes | Auth Service base URL | - |
| `USER_SERVICE_URL` | yes | User Service base URL | - |
| `REDIS_HOST` | yes | Redis host for rate limiting | `localhost` |
| `REDIS_PORT` | yes | Redis port | `6379` |
| `REDIS_PASSWORD` | no | Redis password | - |
| `RATE_LIMIT_ENABLED` | no | Enable/disable rate limiting | `true` |
| `RATE_LIMIT_RPS` | no | Requests per second (token refill rate) | `20` |
| `RATE_LIMIT_BURST` | no | Burst capacity (max tokens in bucket) | `40` |
| `RATE_LIMIT_TRUSTED_PROXIES` | no | Comma-separated IPs trusted for X-Forwarded-For | - |
| `FORWARDING_MAX_BODY_SIZE` | no | Max request body size in bytes | `10485760` (10 MB) |

### MySQL (User Service)

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `USER_SERVICE_MYSQL_DB_ROOT_PASSWORD` | yes | MySQL root password | `root` |
| `USER_SERVICE_MYSQL_DB_NAME` | yes | Database name | `user_service_db` |
| `USER_SERVICE_MYSQL_DB_USER` | yes | Application DB user | `user` |
| `USER_SERVICE_MYSQL_DB_PASSWORD` | yes | Application DB user password | `user1234` |
| `USER_SERVICE_MYSQL_DB_PORT` | yes | Host port mapped to MySQL | `3306` |

### Redis (Auth + Rate Limiting)

| Variable | Required | Description | Example |
|----------|----------|-------------|---------|
| `AUTH_REDIS_PASSWORD` | yes | Redis password | `redis1234` |

---

<a id="common-issues"></a>
## Common Issues
[Back to Table of Contents](#toc)

1. **`JWT secret must be at least 64 bytes for HmacSHA512`** - the `JWT_SECRET` environment variable must be a Base64-encoded string that decodes to at least 64 bytes. Generate one with: `openssl rand -base64 64`.

2. **`401 Unauthorized` on all requests** - ensure `JWT_SECRET` matches the key used by Auth Service to sign tokens. The gateway verifies using HMAC-SHA512 and also checks the `type` claim is `"access"`.

3. **`429 Too Many Requests`** - the rate limiter has exhausted the bucket for your IP or userId. Wait for the duration in the `Retry-After` header. To adjust limits, change `RATE_LIMIT_RPS` and `RATE_LIMIT_BURST`.

4. **`502 Bad Gateway`** - the downstream service is unreachable. Check that Auth Service and User Service are running and accessible at the configured URLs. Inspect logs: `docker compose logs api-gateway-service`.

5. **`503 Service Unavailable`** - the Circuit Breaker has opened for a downstream target after repeated failures. It auto-resets after 30 seconds. Check downstream service health.

6. **Rate limiting not working** - verify Redis is reachable and `RATE_LIMIT_ENABLED=true`. Without Redis the `ProxyManager` bean is not created and rate limiting is silently disabled.

7. **Port conflict** - check for conflicts on `8085` (gateway), `8084` (auth), `8083` (users), `3306` (MySQL), `6379` (Redis): `netstat -ano | findstr :8085`.

---

<a id="architecture"></a>
## Architecture
[Back to Table of Contents](#toc)

```mermaid
graph LR
    classDef presentation fill:#4a90d9,stroke:#2c5f8a,color:#fff
    classDef application fill:#7b68ee,stroke:#4a3aa0,color:#fff
    classDef domain fill:#27ae60,stroke:#1a7a42,color:#fff
    classDef infra fill:#e67e22,stroke:#a85a0f,color:#fff
    classDef external fill:#c0392b,stroke:#8e1a1a,color:#fff

    C([Client]) --> GC[GatewayController]

    subgraph APPLICATION["Application"]
        GC --> GP[GatewayPort] --> GS[GatewayServiceImpl]
    end

    subgraph DOMAIN["Domain"]
        GS --> RT[RoutingTable]
    end

    subgraph INFRASTRUCTURE["Infrastructure"]
        JF[JwtAuthorizationFilter] --> JTA[JwtTokenVerificationAdapter]
        RLF[RateLimitFilter] --> RD
        GS --> FP[ForwardingPort] --> HFA[HttpForwardingAdapter<br/>CircuitBreaker]
    end

    subgraph EXTERNAL["External"]
        HFA --> AS[(Auth Service)]
        HFA --> US[(User Service)]
        RD[(Redis)]
    end

    class C,GC presentation
    class GP,GS application
    class RT domain
    class JF,JTA,RLF,HFA,FP infra
    class AS,US,RD external
```

**Technical Highlights:**

- **Hexagonal Architecture (Ports & Adapters):** Domain and application layers have zero infrastructure dependencies. `ForwardingPort` and `TokenVerificationPort` are the only bridges between application and infrastructure, implemented by `HttpForwardingAdapter` and `JwtTokenVerificationAdapter`. Architectural boundaries are enforced at build time by **ArchUnit** tests (`HexagonalArchitectureTest`) - verifying layer dependencies, framework isolation (domain and application free of Spring/Jakarta imports), and that all ports are interfaces.
- **Header Security:** Incoming request headers are filtered through a whitelist (`content-type`, `accept`, `cache-control`) - `Authorization`, `Host`, `Cookie`, and all other headers are never forwarded. Downstream response headers are stripped of hop-by-hop (`transfer-encoding`, `connection`, `keep-alive`) and server-revealing (`server`, `x-powered-by`) headers.
- **Trusted User Context:** After JWT verification, the gateway injects `X-User-Id`, `X-User-Name`, and `X-User-Role` headers into downstream requests. Downstream services trust these headers and do not re-verify the JWT.
- **Distributed Rate Limiting:** Bucket4j with Redis (Lettuce) provides per-client rate limiting that works across multiple gateway instances. Authenticated users are keyed by `userId`, anonymous users by client IP. `X-Forwarded-For` is only trusted when the direct connection comes from a configured trusted proxy.
- **Circuit Breaker:** Each downstream target gets its own Resilience4j `CircuitBreaker` instance (50% failure threshold, 10-call sliding window, 30 s open duration). When a downstream fails repeatedly, requests fail fast with `502` instead of waiting for timeouts.
- **Path Traversal Protection:** `HttpForwardingAdapter` normalizes request paths via `URI.normalize()` and rejects any path containing `../`.
- **X-Request-Id Propagation:** The gateway reuses a client-provided `X-Request-Id` or generates a UUID, attaches it to downstream requests and response headers for distributed tracing.
- **Virtual Threads:** `spring.threads.virtual.enabled=true` with container-aware JVM flags (`-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC`).
- **Body Size Limit:** `GatewayController` enforces a configurable max body size (default 10 MB) and returns `413 Payload Too Large` on overflow.
- **Conditional Rate Limiting:** The rate limiter is conditionally enabled via `@ConditionalOnProperty`. When disabled (or Redis unavailable at startup), the gateway operates without rate limiting.

---

<a id="tech-stack"></a>
## Tech Stack
[Back to Table of Contents](#toc)

| Layer | Technology |
|-------|------------|
| Language | Java 25 (virtual threads via Project Loom) |
| Framework | Spring Boot 4.0.6 |
| Web | Spring WebMVC |
| Security | Spring Security (stateless JWT, CORS, role-based access) |
| Token verification | jjwt 0.12.5 (HMAC-SHA512) |
| Rate limiting | Bucket4j 8.18.0 + Redis (Lettuce) |
| Resilience | Resilience4j 2.3.0 (CircuitBreaker per downstream target) |
| HTTP client | Spring RestClient (JDK HttpClient, virtual thread executor) |
| Validation | Spring Validation |
| Monitoring | Spring Actuator |
| Build | Maven 3.9, multi-stage Docker build |
| Containerisation | Docker, Docker Compose v2+, non-root user, layer extraction |
| CI/CD | GitHub Actions (CI pipeline + Docker Hub publish) |
| Architecture tests | ArchUnit 1.4.2 (hexagonal boundary enforcement) |
| Utilities | Lombok |

---

<a id="testing-strategy"></a>
## Testing Strategy
[Back to Table of Contents](#toc)

### Unit Tests

Plain JUnit 5 + Mockito, no Spring context loaded.

| Class | Key Scenarios |
|-------|--------------|
| `GatewayServiceImplTest` | Route resolution, header whitelist filtering, cookie exclusion, X-Request-Id reuse/generation/propagation, X-User-* enrichment for authenticated users, omission for anonymous, hop-by-hop header stripping, query string propagation |
| `GatewayControllerTest` | GET/POST forwarding, body propagation, empty body for GET, response header propagation, query string forwarding, 404 for unknown route, 502 for downstream unavailable |
| `RoutingTableTest` | Prefix-based route matching |
| `JwtTokenVerificationAdapterTest` | Token verification, claim extraction |
| `RateLimitFilterTest` | Rate limit enforcement (consume/reject), key resolution (userId vs IP), X-Forwarded-For trust with trusted proxies |
| `HexagonalArchitectureTest` | ArchUnit - layer dependencies, framework isolation (domain/application free of Spring/Jakarta), ports are interfaces |

### Integration Tests

Full Spring Boot context with Redis (Testcontainers). All IT classes extend `AbstractIntegrationTest` which starts a Redis container and injects dynamic properties for JWT secret and Redis connection.

| Class | Key Scenarios |
|-------|--------------|
| `SecurityIntegrationTest` | Public endpoints accessible without token, protected endpoints return 401 without token, RBAC (admin can access admin paths, user cannot, both can access user paths), expired/malformed/wrong-signature tokens return 401 |
| `RateLimitIntegrationTest` | Returns 429 after burst capacity exceeded, different IPs have independent limits, `X-Rate-Limit-Remaining` header present on successful requests |

### Infrastructure

| Component | Test setup |
|-----------|------------|
| Redis | Testcontainers (`redis:8.2-alpine`), singleton per JVM |
| JWT | Test-only HMAC-SHA512 key (64 bytes, deterministic) |
| Downstream services | `@MockitoBean` on `ForwardingPort` |

### Running Tests

```bash
mvn test                  # unit tests only
mvn verify                # unit + integration tests
```

---

<a id="repository-structure"></a>
## Repository Structure
[Back to Table of Contents](#toc)

```text
.
├── src/
│   ├── main/
│   │   ├── java/com/rzodeczko/
│   │   │   ├── application/
│   │   │   │   ├── port/
│   │   │   │   │   ├── in/  GatewayPort
│   │   │   │   │   └── out/ ForwardingPort, TokenVerificationPort
│   │   │   │   └── service/
│   │   │   │       └── impl/ GatewayServiceImpl
│   │   │   ├── domain/
│   │   │   │   ├── exception/
│   │   │   │   │   ├── DownstreamUnavailableException
│   │   │   │   │   ├── InvalidTokenException
│   │   │   │   │   ├── PayloadTooLargeException
│   │   │   │   │   └── RouteNotFoundException
│   │   │   │   └── model/
│   │   │   │       ├── GatewayRequest         # domain request (path, method, headers, body, query)
│   │   │   │       ├── GatewayResponse         # domain response (status, headers, body)
│   │   │   │       ├── RoutingTable            # prefix-based route resolution
│   │   │   │       └── TokenInfo               # JWT claims (userId, username, role)
│   │   │   ├── infrastructure/
│   │   │   │   ├── configuration/
│   │   │   │   │   ├── BeanConfiguration       # wires domain beans, RestClient, Redis, RoutingTable
│   │   │   │   │   └── properties/
│   │   │   │   │       ├── GatewayProperties    # routes, CORS, rate limit, forwarding config
│   │   │   │   │       └── JwtProperties        # JWT secret
│   │   │   │   ├── forwarding/
│   │   │   │   │   └── HttpForwardingAdapter    # RestClient + CircuitBreaker per target
│   │   │   │   ├── security/
│   │   │   │   │   ├── SecurityConfiguration    # filter chain, CORS, RBAC rules
│   │   │   │   │   └── filter/
│   │   │   │   │       ├── AccessLogFilter       # structured request logging
│   │   │   │   │       ├── JwtAuthorizationFilter # JWT verification + SecurityContext
│   │   │   │   │       └── RateLimitFilter        # Bucket4j + Redis rate limiting
│   │   │   │   └── token/
│   │   │   │       └── JwtTokenVerificationAdapter # HMAC-SHA512 token verification
│   │   │   ├── presentation/
│   │   │   │   ├── controller/
│   │   │   │   │   ├── GatewayController        # catch-all /** proxy controller
│   │   │   │   │   └── HealthCheckController     # health endpoint
│   │   │   │   ├── dto/ HealthCheckResponseDto
│   │   │   │   └── exception/ GlobalExceptionHandler
│   │   │   └── ApigatewayServiceApplication
│   │   └── resources/
│   │       └── application.yaml
│   └── test/
│       └── java/com/rzodeczko/
│           ├── application/service/impl/ GatewayServiceImplTest
│           ├── domain/model/ RoutingTableTest
│           ├── infrastructure/
│           │   ├── security/filter/ RateLimitFilterTest
│           │   └── token/ JwtTokenVerificationAdapterTest
│           ├── integration/
│           │   ├── AbstractIntegrationTest      # Redis Testcontainers + JWT key setup
│           │   ├── RateLimitIntegrationTest      # 429, independent IP limits
│           │   └── SecurityIntegrationTest       # public/protected/RBAC/invalid tokens
│           ├── presentation/controller/ GatewayControllerTest
│           └── HexagonalArchitectureTest         # ArchUnit boundary enforcement
├── .github/workflows/
│   ├── ci.yml                                   # build + test + JaCoCo + Codecov
│   └── docker-publish.yaml                      # manual trigger, CI gate, Docker Hub push
├── docker-compose.yml                           # MySQL + Redis + Auth + Users + Gateway
├── Dockerfile                                   # multi-stage build (maven -> jre-alpine, non-root)
├── pom.xml
└── README.md
```

---

<a id="contact"></a>
## Contact
[Back to Table of Contents](#toc)

Designed and implemented by **Michal Rzodeczko**.

GitHub: [mrzodeczko-dev](https://github.com/mrzodeczko-dev)