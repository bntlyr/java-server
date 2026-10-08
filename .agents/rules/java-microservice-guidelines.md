# Java Microservice & Processing Conventions

## Schema Safety & Model Strategy
- Set `spring.jpa.hibernate.ddl-auto=none` so Hibernate never alters tables managed by Prisma.
- Do **NOT** duplicate the full database schema. Only define JPA entities or `JdbcTemplate` queries needed for active worker tasks.
- Use Java 21 `record` types for immutable request and response DTOs.

## Security & Endpoints
- Public endpoints: `/actuator/health/**`, `/actuator/info`.
- Protected endpoints: Validate `X-Internal-Secret` using `InternalSecretFilter` for all `/api/**` routes.
- Processing endpoints:
  - `POST /api/v1/process`: Synchronous execution.
  - `POST /api/v1/process/async`: Asynchronous execution using virtual threads / CompletableFuture.

## Observability & Logging
- Use `RequestLoggingFilter` with SLF4J MDC to attach `traceId` (`X-Request-Id`) and log request method, path, client IP, status, and duration in ms.
- Format console and rolling file logs via `logback-spring.xml`.
- Handle unhandled errors globally with `GlobalExceptionHandler` returning structured JSON payloads with `traceId`.
