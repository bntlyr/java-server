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

## PDF Export, Template Stamping & QR Engine Conventions
- **Self-Contained Endpoint Configuration Invariant**: PDF generator endpoints (`/api/v1/export/pdf`) must receive all document geometry, margins, table styling (`rowGap`, `colGap`), and watermark configs directly in the request body (`PdfConfigDto`), avoiding global/centralized config dependencies.
- **Template Stamping Engine**:
  - Use OpenPDF `PdfStamper` with `sample-template.pdf` (A4 Landscape: $842 \times 595$) and `sample-trial-template.pdf` (A4 Portrait: $595 \times 842$) for pixel-exact document coordinate positioning.
  - Parse coating patterns (e.g. `E5.5D3A`) and fractional slip sizes (`1/2`) with dedicated regex parsers (`PatternParserUtil`).
- **Document Variants & Overlays**:
  - `ORIGINAL`: Standard production traveler / trial card.
  - `REWORK`: Renders `reworkLotNo` at $(40, 30)$ with `REWORK` watermark.
  - `REMAKE`: Applies `REMAKE` diagonal watermark overlay ($0.18$ alpha, red).
  - `TRIAL`: Applies large center `TRIAL` watermark ($0.20$ alpha) and renders `trialLotNo`.
- **Dynamic Table Engine**:
  - Tables must support explicit `rowGap` (vertical cell padding) and `colGap` (horizontal cell padding) for spacious layouts.
  - Support embedded images and QR codes directly inside table cells.
- **QR Code Service**:
  - `QrCodeService` (ZXing) must accept either raw `String` or complex `Object`/JSON payloads (serialized via Jackson).
- **Batch Concatenation**:
  - Merge multi-slip orders into a single PDF document using `PdfCopy` for batch printing.

