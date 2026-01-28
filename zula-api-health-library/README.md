# Zula API Health Library

Lightweight API-call health and visibility package. It auto-creates the required tables (if enabled), exposes REST endpoints to inspect external API usage, and provides an annotation to register the APIs you call.

## What you get
- Auto-configuration (Spring Boot 3) – no manual wiring.
- Schema/tables auto-created via `zula-database-library` (configurable).
- REST endpoints:
  - `GET /api/health/endpoints` – tracked endpoints + basic metrics.
  - `GET /api/health/endpoints/{id}` – detail + recent logs.
  - `GET /api/health/logs/recent?limit=50` – latest external call logs.
  - `GET /api/health/logs/by-endpoint?url=...&limit=50` – logs filtered by endpoint URL.
- Annotation `@TrackApiEndpoint` to register outbound APIs you call (path/method/description).

## Install
```xml
<dependency>
  <groupId>com.zula</groupId>
  <artifactId>zula-api-health-library</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Configuration (application.yml)
```yaml
zula:
  apihealth:
    schema-name: customer_onboarding   # optional; defaults to derived service schema
    auto-create-tables: true          # create schema/table if missing
    recent-limit: 50                  # default page size
```

## Annotation example
```java
@TrackApiEndpoint(path="https://r1l32.wiremockapi.cloud/test", method="POST", description="Wiremock demo")
public ResponseEntity<String> callPartner(...) { ... }
```

## Publishing
Configure your Maven `settings.xml` GitHub Packages creds (id `github`), then:
```
mvn -DskipTests=true clean deploy
```

License: MIT (align with other Zula packages).
