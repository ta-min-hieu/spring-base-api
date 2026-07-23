# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Read `AGENTS.md` alongside** — it holds the mandatory coding rules (in Vietnamese): uniform response/error idiom, IAM pitfalls, outbound HTTP & circuit breaker, inbound rate limiting, Redis usage, opt-in infra enable flags, i18n, config sync, Definition of Done. This file describes *what exists and why* (facts you can't cheaply re-derive from the code); it does not repeat those rules — pointers like "AGENTS.md §B" below refer to its sections.

## What this is

`com.ringme.base` — a Spring Boot 4.1 / Java 25 REST API originally scaffolded as a **base/skeleton** to be cloned for new services (`spring/base/4.0-jdk25-full` branch, migrated from the Boot 3.5 / Java 21 base). It ships cross-cutting plumbing (JWT auth, request context, i18n responses, uniform error handling, HTTP client wrapper, CORS, profiles). **This instance has been extended past the skeleton:**

- **Product domain** (`controller/v1/{Product,File}Controller`, Oracle schema `dev_e_commerce`): product CRUD — the list endpoint takes optional `name`/`category`/`status` filters, AND-combined in one JPQL query (`ProductRepository.search`) — plus image/video upload including chunked upload for large files.
- **IAM / RBAC module** (an `iam` subpackage inside each layer, schema `dev_iam` — see its section below): User/Role/Permission/Menu management under `/v1/rbac/**`, dynamic per-request authorization, and Keycloak login as an alternative to the built-in JWT issuer.
- `AuthServiceImpl.authenticateCredentials` is **implemented** (BCrypt against `dev_iam.app_user`, roles from `dev_iam.user_role`) — not the template's 401 stub.
- `AppCode` holds only generic codes; business errors reuse `CODE_400`/`CODE_404` with a descriptive message (no domain code enum yet).
- **Lite vs full:** `-lite` branches ship no 3rd-party infra; this **`-full`** branch adds opt-in (disabled-by-default) MongoDB/RabbitMQ/Kafka/Redis/Consul, plus an **always-on Oracle datasource** that replaced the template's opt-in MySQL (see **Oracle: two schemas, one datasource**).

## Commands

Maven wrapper (`./mvnw` / `mvnw.cmd`). **Java 25 required** — `maven-enforcer-plugin` enforces `[25,26)` at `validate`; point `JAVA_HOME` at a JDK 25. Lombok is declared explicitly in `maven-compiler-plugin` `<annotationProcessorPaths>` (JDK 23+ no longer auto-runs classpath-only annotation processors — without it every `@Getter`/`@Builder`/`@Log4j2` member fails to compile).

```bash
./mvnw spring-boot:run                             # dev profile → http://localhost:8386/base
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run # env-driven prod config
./mvnw test                                        # all tests
./mvnw test -Dtest=AuthControllerTest#login_issuesTokenPair   # single method (or just the class)
./mvnw clean package                               # exploded jar: target/base-0.0.1.jar + target/lib/
```

The jar is **exploded (non-fat)**: `maven-jar-plugin` sets `mainClass=com.ringme.base.BaseApplication` + `classpathPrefix=lib/`; run `java -jar target/base-0.0.1.jar` next to its `lib/` folder.

**Docker & deploy.** `Dockerfile` is multi-stage (`maven:3.9-eclipse-temurin-25` build → `eclipse-temurin:25-jre` run). The root `docker-compose.yml` does **not** publish ports — the single entry point is a **shared nginx** outside this repo at `E:/study/shared-nginx` (Docker network `shared-edge`, shared with the Angular frontend project; route `/base/**` → this service). Create the network once (`docker network create shared-edge`), then `docker compose up --build -d`. Helper compose files for local infra live under `docker/` (kafka, rabbitmq, mongodb, mariadb, redis-cluster, redis-sentinel, keycloak, postgres); `docker/postgres` builds a local image `base-postgres:16-vn-tz` restoring the legacy `Asia/Saigon` tzdata alias (postgres:16 dropped it — clients sending the old name failed the connection handshake). CI (`.github/workflows/ci.yml`): `./mvnw -B clean verify` on JDK 25.

API docs: Swagger UI at `http://localhost:8386/base/swagger-ui.html`. `postman/base-api-openapi.json` is an export of `/v3/api-docs` for Postman import — **regenerate it after API changes** (it is not auto-synced).

## Configuration & profiles

App-specific properties use the **`app.*`** prefix (`app.jwt.*`, `app.domain.*`, `app.img/audio/avatar.*`, `app.cors.*`, `app.rate-limit.*`, `app.rbac.*`, `app.keycloak.*`); infra properties stay native/root (`cache.*`, `rest-template.*`, `spring.data.redis.*`, `app-security`, ...). Most values are env-overridable with sensible defaults (e.g. `${JWT_ACCESS_TTL:86400}`).

**Per-profile folders.** Only `application.yaml` sits at the root of `resources/` — app name, active-profile wiring, the profile-directory import, and the **Spring Cloud Consul** block (must live here: `spring.config.import: optional:consul:` resolves at bootstrap, before profile files load). Environment config lives in `resources/profiles/<profile>/`, split by concern: `server.yaml`, `web.yaml`, `security.yaml`, `redis.yaml`, `http-client.yaml`, `cache.yaml`, `observability.yaml`, `resilience.yaml`, `oracle.yaml` (always-on datasource), plus one file per opt-in infra: `mongodb.yaml`, `rabbitmq.yaml`, `kafka.yaml`. (`mysql.yaml` no longer exists in any profile — see the MySQL note under **Opt-in infra**.)

**Directory-import mechanism.** `spring.config.import` has no classpath wildcards; a directory import only loads base names listed in `spring.config.name`. It is therefore set to `application,server,web,security,redis,http-client,cache,observability,resilience,mysql,mongodb,rabbitmq,kafka,oracle` (`application` must stay in the list so the root file itself loads; `mysql` is vestigial but harmless) in **two places that must stay in sync**:
- **Runtime:** `BaseApplication.main()` via `setDefaultProperties` — travels with the jar, no env var needed.
- **Tests:** `maven-surefire-plugin` `systemPropertyVariables` — `@SpringBootTest` never calls `main()`.

**Adding/removing a profile file ⇒ update both.** New environment = new `resources/profiles/<name>/` folder with the same file set + `SPRING_PROFILES_ACTIVE=<name>`. Current: `dev` (default; permissive CORS, bundled dev keys), `uat`, `prod` (Swagger off; keys/CORS/secret forced from env).

**Secrets & JWT keys:** real deployments must supply `APP_SECURITY`, `JWT_PRIVATE_KEY_PATH`, `JWT_PUBLIC_KEY_PATH` via env (see `.env.example`). Keys under `resources/keys/` are **dev-only** (loaded by `RsaKeyLoader`); every clone regenerates its own PKCS#8/X.509 pair via `./scripts/generate-keys.sh` or `.ps1` (openssl wrappers with an overwrite guard).

## Architecture

**Request lifecycle (filter order matters):**
1. `RequestContextFilter` (`@Order(HIGHEST_PRECEDENCE)`) — wraps request/response in content-caching wrappers, builds a `RequestContext` from headers (`requestId`, `language`, `msisdn`, `deviceId`, ...) into a ThreadLocal (`RequestContextHolder`), puts `contextId` into Log4j2 MDC, and on completion logs request+response (bodies truncated to 4KB) to the `"request"` logger — sensitive headers/JSON fields masked by `LogMasker` first. Always clears ThreadLocal/MDC in `finally`.
2. `RateLimitFilter` (opt-out, per-client-IP, Resilience4j; full behavior/config rules in AGENTS.md §B) — after the context filter (so the 429 is localized), before Spring Security. Per-instance backstop only; global limits belong at CDN/WAF/gateway.
3. `JwtAuthenticationFilter` (before `UsernamePasswordAuthenticationFilter`) — `Authorization: Bearer` → `JwtAuthenticationServiceImpl`/`JwtProcessor`; the subject becomes the principal, the `roles` claim becomes `ROLE_*` authorities (so `@RolesAllowed`/`hasRole` work).

**Auth flow:** `POST /v1/auth/login` issues an access+refresh pair (RS512), roles read fresh from `dev_iam.user_role` (ACTIVE roles only) — the sole source of truth. `POST /v1/auth/refresh-token` **re-reads the user from DB** (checks `enabled`, recomputes roles) instead of copying the old token's claims — disables/role changes bite on the next refresh. Tokens carry a `type` claim; only `type=access` authenticates requests, so a refresh token can't call APIs.

**Login v2 (Keycloak), opt-in** (`app.keycloak.enabled` / `KEYCLOAK_LOGIN_ENABLED`): `POST /v2/auth/login|refresh-token` (`controller/v2/AuthController`) proxy Keycloak's Direct Access Grant / refresh grant via `KeycloakAuthClient` and return Keycloak's tokens verbatim. When a Keycloak token later hits any protected endpoint, `JwtAuthenticationServiceImpl.authenticateWithKeycloak` verifies it via JWKS (`KeycloakJwtDecoderHolder`), requires `typ=Bearer` (Keycloak signs refresh/ID tokens with the same key — without this a refresh token would authenticate), then resolves roles:
- If the token's `preferred_username` (**not** `sub`) matches a `dev_iam.app_user` row, that row's `user_role` is the **sole** role source — Keycloak's `realm_access.roles` is ignored entirely, even when the result is empty (revoking all roles locks the user out immediately).
- Identity binding is **trust-on-first-use** keyed on the stable `sub` claim (stored in `app_user.keycloak_subject`); a later login for the same username with a different `sub` is rejected — stops a different Keycloak identity from impersonating a local account via username reuse.
- No matching `app_user` ⇒ Keycloak-only identity, falls back to `realm_access.roles`.

Keycloak persists to real Postgres (`docker/postgres`, database `keycloak`) — see `docker/keycloak/docker-compose.yml`. Re-importing a realm export **rotates the client secret**; resync `KEYCLOAK_CLIENT_SECRET` or v2 login fails with `unauthorized_client`.

**Security** (`SecurityConfig`): stateless, CSRF off, CORS from `app.cors.allowed-origins`, `@EnableMethodSecurity(jsr250Enabled = true)`. Public paths (relative to the `/base` context): `/v1/auth/**`, `/v2/auth/**`, `/troubleshoot/**`, `/actuator/health/**`, swagger/api-docs, `/v1/core/bonus-turn`, all `OPTIONS`. Everything else requires authentication.

## IAM / RBAC module

Packages group by **domain**, not `controller/vN`: an `iam` subfolder inside each layer (`controller/iam`, `service/iam`, `service/impl/iam`, `entity/iam`, `repository/iam(/projection)`, `dto/app/{request,response}/iam`, `enums/iam`, `security/iam`, `filter/iam`, `config/iam`). URLs are unaffected — everything is `/v1/rbac/**`. Schema `dev_iam`.

- **Entities:** `AppUser`, `Role`, `Permission` (1 row = 1 API resource: HTTP method + Ant URL pattern, e.g. `GET /v1/products/**`), `Menu` (self-referencing UI tree via `parentId`, independent of `Permission`), plus join entities `UserRole`/`RolePermission`/`RoleMenu`.
- **Dynamic per-request authorization** (`DynamicPermissionFilter`, gated by `app.rbac.enabled`/`RBAC_ENABLED`, off by default): wired **explicitly** in `SecurityConfig` via `addFilterAfter(..., AuthorizationFilter.class)` + a matching `accessDeniedHandler`, with the auto-registered duplicate disabled (`dynamicPermissionFilterRegistration`) — wiring pitfalls in AGENTS.md §B-IAM. Checks the caller's `ROLE_*` authorities against `role_permission`, cached per role key (`PermissionCacheService`, `KeyCache.ROLE_PERMISSIONS`). Roles in `app.rbac.super-admin-roles` (default `[ADMIN]`) bypass the check. New APIs are authorized by inserting a `Permission` row + assigning it — no redeploy.
- **Management API**, all `/v1/rbac/**`, gated by `@RolesAllowed("ADMIN")` (static method security — works even with `RBAC_ENABLED=false`): `UserController` (CRUD `app_user`), `RoleController`/`PermissionController`/`MenuController` (CRUD + assignment sub-resources `/roles/{id}/permissions`, `/roles/{id}/menus`), `UserRoleController` (`PUT /users/{userId}/roles` **replaces** the full role set). Admin self-lockout (self-delete/disable/strip-all-roles) is blocked; `PUT` treats omitted fields as "keep current value" — rules in AGENTS.md §B-IAM.
- **Self-service** (`RbacMeController`, excluded from the dynamic filter via `app.rbac.excluded-paths`): `GET /v1/rbac/me/menus` (tree merged across the caller's roles) and `GET /v1/rbac/me/permissions`.
- `PermissionEnrichmentService` adds `PERM_<code>` authorities to the principal (same cached data) for `@PreAuthorize("hasAuthority('PERM_...')")` — an alternative to the URL-pattern filter.

## Oracle: two schemas, one datasource

Always-on Oracle datasource (`profiles/<env>/oracle.yaml`, all of dev/uat/prod; `uat`/`prod` require `DB_DWH_URL`/`DB_DWH_USERNAME`/`DB_DWH_PASSWORD` with no bundled default). Two schemas share the single datasource/pool, distinguished purely by each `@Entity`'s `@Table(schema = ...)`:
- **`dev_e_commerce`** — `Product`, `ProductUploadFile`, `UploadFile` (business domain).
- **`dev_iam`** — the whole IAM module.

The connecting DB user needs explicit cross-schema `GRANT SELECT, INSERT, UPDATE, DELETE` on every `dev_iam.*` table — `sql/oracle/iam.sql` part (3). No second datasource; Hibernate emits fully-qualified table names. `mysql.yaml` was removed from all profiles because its `DataSourceAutoConfiguration` exclusion would silently kill the Oracle datasource too (Boot creates only one `DataSource` bean); `mysql-connector-j` is commented out in `pom.xml`.

## Core idioms (rules + examples in AGENTS.md §B)

- **Uniform responses:** controllers never build `ResponseEntity` — return `AppCode.<CODE>.getResponse(payload)` → `Response<T>` `{code, message, data, metadata}` (nulls omitted), message localized from the request's `language` via `MultiLangManager`. **`getResponse()`/`getMessageLang()` read the request-scoped ThreadLocal — never call them from `@Async`/off-request threads.** `AppCode` resolves `MultiLangManager` lazily via `BeanUtil` (enums can't be Spring-injected).
- **Errors:** throw `BusinessLogicException(AppCode, ...)`; `GlobalExceptionHandler` returns the `AppCode` body **and** sets the HTTP status from `AppCode.toHttpStatus()`; validation failures are collected into a `{field: message}` map as `CODE_400`; any other `Exception` → `CODE_500`. `isLog`/`isTraceStackFull` control logging.
- **Outbound HTTP:** extend `RestAbstractHttpClient` + `defaultHeaders()` over the pooled `RestTemplate` (`RestTemplateConfig`, `rest-template.*`). Pool sizing is the real concurrency ceiling (virtual threads queue on the pool-lease `request-timeout`); transport retry covers idempotent methods only; upstream error bodies pass through with their status, connection failures → 503. **Circuit breaker is opt-in per client** (shared `outboundHttpCircuitBreaker` bean, or per-upstream via `super(restTemplate, registry, "<name>")` — preferred with multiple upstreams); thresholds centralized in `resilience.yaml` `resilience4j.circuitbreaker.configs.default`; upstream 4xx/5xx never trip it. Pool metrics `http.client.pool.*` via `ConnPoolMeters` (bound after `setConnectionManager`); `LoggingInterceptor` buffers/logs only at DEBUG, masked via `LogMasker`.
- **Request-param enums:** converters live in `converter/request` (`StatusEnumConverter`, `ProductStatusEnumConverter`) — `@Component implements Converter<String, E>`; invalid input logs + returns `null` so the param behaves as absent instead of erroring the request.

## Opt-in infra (all default-off; enable flags + Boot 4 gotchas in AGENTS.md §B)

The base builds/tests/runs with none of these installed. Enabled paths are verified by Testcontainers tests (`infra/{kafka,mongodb,rabbitmq}`, `RedisIntegrationTest`, `ConsulConfigIntegrationTest`) that **self-skip without Docker**, so `./mvnw test` stays green.

- **Redis (cache + Redisson distributed lock)** — `redis.yaml`, native `spring.data.redis.*`, gate `REDIS_ENABLED`; standalone vs cluster inferred from `cluster.nodes`. `CaffeineConfig` is always-on (`CacheManager.CAFFEINE`, the default manager when Redis is off; stays addressable by name for hot local caches when on); `RedisConfig` adds a `@Primary` `RedisCacheManager` with per-cache TTLs from **`KeyCache`** (currently `ROLE_PERMISSIONS`/`ROLE_MENUS`, 6h, for IAM — add new cache constants there; `null` TTL = default). Boot 4 notes: `RedisProperties` → `DataRedisProperties` (moved package); Jackson 3 `GenericJacksonJsonRedisSerializer` (jsr310 built-in).
- **Consul (config + discovery)** — whole block in root `application.yaml` (bootstrap-time import), gate `CONSUL_ENABLED`; KV config on by default when enabled, discovery separately via `CONSUL_DISCOVERY_ENABLED`. Spring Cloud BOM `2025.1.2`. Its test passes container coords via **system properties in a static block** — the config-import phase runs before `@DynamicPropertySource`.
- **MongoDB** — Boot 4 moved the prefix `spring.data.mongodb.*` → **`spring.mongodb.*`** (old prefix silently falls back to `localhost:27017`); the URI must include a database name. Gate `MONGODB_ENABLED`; health key `management.health.mongodb.enabled`.
- **RabbitMQ** — lazy connect, no `@RabbitListener` in the base. Gate `RABBITMQ_ENABLED`; cluster via `spring.rabbitmq.addresses`; health key `management.health.rabbit.enabled`.
- **Kafka** — dependency is **`spring-boot-kafka`**, not bare `spring-kafka` (Boot 4 moved `KafkaAutoConfiguration` there; bare `spring-kafka` gives the classes but no beans). No health indicator — convention gate `KAFKA_ENABLED`; set `admin.fail-fast:false` so a missing broker doesn't fail startup.
- **MySQL — vestigial**, replaced by Oracle (see above). `mysql` stays harmlessly in `spring.config.name`; restoring it means re-adding `profiles/<env>/mysql.yaml` and picking one of MySQL/Oracle per environment.

## Conventions

- **Log4j2, not Logback** (`spring-boot-starter-logging` excluded in `pom.xml`). Use `@Log4j2`; config `log4j2.xml`; logs under `logs/`; per-request access logs on the `"request"` logger.
- Lombok throughout; constructor injection via `@RequiredArgsConstructor` on `final` fields.
- Package-by-concern: `config/` (one subpackage per concern), `controller/v1` (versioned), `service` + `service/impl`, `dto/app/{request,response}`, `enums`, `exception`, `filter`, `security`, `client`, `context`, `converter`. **IAM is the one exception** — domain-grouped `iam` subfolders inside each layer.
- Comments and Swagger `@Tag`/`@Operation` descriptions are **Vietnamese** — match the surrounding language.
- New endpoints: controller under `controller/vN`, return `AppCode.<CODE>.getResponse(...)`, add any new public path to `SecurityConfig`. Domain response codes go in a **separate enum**, not `AppCode`. `AppCode.matches(String)`/`TokenType.matches(String)` compare raw values — they are *not* `equals` overrides.

## Reusing as a template

The clone checklist (rename artifact/package, regenerate JWT keys, set secrets, implement/keep auth, add domain codes) lives in **AGENTS.md §A**. It describes cloning this repo for a *new* service — steps 1–4 are already done in this repo (Product + IAM + Oracle + real auth); regenerate `dev_iam` data (`sql/oracle/iam.sql`) rather than inheriting this clone's users/roles.
