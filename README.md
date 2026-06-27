# Security Platform

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Monorepo for a microservice-based authentication and user management platform, built with **Spring Boot 4.0.6**, **Java 25**, and **Hexagonal Architecture**.

## Services

| Service | Port | Description | README |
|---------|------|-------------|--------|
| **API Gateway** | 8085 | JWT auth, RBAC, rate limiting, circuit breaker, request forwarding | [services/api-gateway/README.md](services/api-gateway/README.md) |
| **Auth Service** | 8084 | Login, MFA (TOTP), JWT token pair with JTI binding & rotation, server-side revocation | [services/auth-service/README.md](services/auth-service/README.md) |
| **User Service** | 8083 | Registration, activation, password reset (one-time token), MFA setup, role management | [services/user-service/README.md](services/user-service/README.md) |

## Quick Start

```bash
cp .env.example .env
# Fill in secrets (JWT_SECRET, DB passwords, SMTP credentials, INTERNAL_SECRET)
docker compose up -d --build
# Verify
curl http://localhost:8085/actuator/health
```

## Architecture

```mermaid
graph TB
    Client(["Client"])

    subgraph Docker Network
        subgraph Gateway["API Gateway :8085"]
            JWT_FILTER[JWT Filter]
            RATE_LIMIT[Rate Limiter]
            RBAC[RBAC Guard]
            CIRCUIT[Circuit Breaker]
        end

        subgraph Auth["Auth Service :8084"]
            LOGIN[Login + MFA]
            TOKEN[Token Management]
            LOGOUT[Logout / Revocation]
        end

        subgraph User["User Service :8083"]
            REG[Registration + Activation]
            RESET[Password Reset]
            MFA_SETUP[MFA Setup]
            ROLES[Role Management]
        end

        REDIS[(Redis)]
        MYSQL[(MySQL)]
    end

    Client -->|":8085 only exposed port"| Gateway

    JWT_FILTER --> RATE_LIMIT --> RBAC --> CIRCUIT

    Gateway -->|"X-Internal-Secret"| Auth
    Gateway -->|"X-Internal-Secret"| User

    Auth -->|"MFA sessions
Refresh tokens (JTI)"| REDIS
    Gateway -->|"Rate limit buckets"| REDIS

    User --> MYSQL

    Auth -->|"Credential
verification"| User

    style Client fill:#e1f5fe
    style Gateway fill:#fff3e0
    style Auth fill:#f3e5f5
    style User fill:#e8f5e9
    style REDIS fill:#ffebee
    style MYSQL fill:#ffebee
```

The gateway is the only publicly exposed service. Auth and User services communicate via internal Docker network with `X-Internal-Secret` header validation.

## Repository Structure

```
security-platform/
├── services/
│   ├── api-gateway/              # Reverse proxy, JWT, rate limiting
│   ├── auth-service/             # Authentication & token management
│   │   └── auth-service/         # Maven project
│   └── user-service/             # User lifecycle management
│       └── user-service/         # Maven project
├── docker-compose.yml            # Full stack (MySQL + Redis + 3 services)
├── .env.example                  # All environment variables
├── pom.xml                       # Maven parent (shared versions)
└── README.md
```

## Build

```bash
# Build all services
mvn clean package -DskipTests

# Build single service
mvn clean package -pl services/api-gateway -DskipTests

# Run all tests
mvn verify
```

## Contact

Designed and implemented by **Michal Rzodeczko**.
GitHub: [mrzodeczko-dev](https://github.com/mrzodeczko-dev)
