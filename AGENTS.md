# AGENTS.md — Quy tắc cho agent khi làm việc trên dự án này

Tài liệu này dành cho AI agent (và lập trình viên) làm việc với codebase **`com.ringme.base`** — một base/skeleton Spring Boot 4.1 / Java 25 (nhánh `spring/base/4.0-jdk25-full`, migrate từ bản Boot 3.5 / Java 21) để clone ra dự án mới. Phân vai với `CLAUDE.md`: `CLAUDE.md` mô tả **cái gì đang tồn tại & vì sao** (kiến trúc, facts); file này là **quy tắc bắt buộc khi code** — hai file bổ trợ nhau, không lặp nội dung. Khi mâu thuẫn, **chỉ dẫn trực tiếp của người dùng > AGENTS.md > mặc định**.

**Lưu ý:** repo NÀY (bản đang mở) đã đi quá phần "skeleton trống" — đã có domain Product (schema `dev_e_commerce`) và module IAM/RBAC đầy đủ (User/Role/Permission/Menu, schema `dev_iam`, cả login Keycloak song song) chạy trên Oracle. Mục A bên dưới là checklist khi CLONE base này ra dự án mới, không phải mô tả trạng thái hiện tại của repo này — xem `CLAUDE.md` mục **"What this is"**, **"IAM / RBAC module"**, **"Oracle: two schemas, one datasource"** cho trạng thái thật.

Ngôn ngữ: **mọi comment, mô tả, message tài liệu trong dự án phải bằng tiếng Việt.**

---

## A. Khi clone base này thành dự án mới — phải sửa gì

Checklist bắt buộc (thiếu bước nào là dự án chưa chạy đúng/an toàn):

1. **Đổi tên định danh dự án**
   - `groupId` / `artifactId` / `version` trong `pom.xml`.
   - Package gốc `com.ringme.base` → package của dự án (đổi cả thư mục).
   - Cập nhật `mainClass` trong `maven-jar-plugin` và package của `@SpringBootApplication`.

2. **Sinh lại cặp khóa JWT** — KHÔNG dùng lại khóa của base/dự án khác.
   ```bash
   ./scripts/generate-keys.sh            # Linux/macOS/Git Bash
   ./scripts/generate-keys.ps1           # Windows PowerShell (cần openssl)
   ```
   Khóa dev ghi vào `src/main/resources/keys/`. Prod: mount khóa ngoài qua `JWT_PRIVATE_KEY_PATH` / `JWT_PUBLIC_KEY_PATH`.

3. **Đặt secret cho môi trường thật** (uat/prod) qua biến môi trường — xem `.env.example`:
   `APP_SECURITY`, `JWT_*_KEY_PATH`, `CORS_ALLOWED_ORIGINS` (KHÔNG dùng `*` ở prod), `CDN_MEDIA_DOMAIN`.

4. **Cài đặt xác thực thật**: bản gốc chưa clone, `AuthServiceImpl.authenticateCredentials()` ném 401 cho mọi request (fail-closed) — thay bằng kiểm tra thông tin đăng nhập thật và trả về danh sách vai trò. **Repo này ĐÃ cài đặt** (BCrypt qua `dev_iam.app_user` + role từ `dev_iam.user_role`, xem `AuthServiceImpl`) — nếu clone từ đây, giữ nguyên module IAM (mục A-IAM bên dưới) hoặc thay bằng cơ chế auth khác tuỳ nhu cầu dự án mới.

5. **Thêm mã nghiệp vụ riêng**: tạo enum mã riêng trong dự án (KHÔNG nhồi vào `AppCode` — `AppCode` chỉ giữ mã chung). Mỗi mã mới phải có key trong **tất cả** file i18n (xem mục D).

6. **Thêm endpoint & đường dẫn public**: controller đặt dưới `controller/vN`; nếu public thì thêm path vào `SecurityConfig.authorizeHttpRequests(...)`.

7. **Đồng bộ tên file config** nếu thêm/bớt file profile (xem mục E).

---

## B. Quy tắc code bắt buộc

- **Response thống nhất**: controller KHÔNG tự dựng `ResponseEntity`. Luôn trả qua `AppCode.<CODE>.getResponse(...)`. Thành công: `return AppCode.CODE_200.getResponse(payload);`
- **Lỗi nghiệp vụ**: ném `BusinessLogicException(AppCode, ...)`; `GlobalExceptionHandler` sẽ set HTTP status theo `AppCode.toHttpStatus()`. KHÔNG trả lỗi bằng tay.
- **Thêm exception mới cần bắt**: bổ sung `@ExceptionHandler` trong `GlobalExceptionHandler`, đặt handler cụ thể TRƯỚC catch-all `Exception` (Spring chọn handler cụ thể hơn). Nếu cần status mới → thêm mã vào `AppCode` + map trong `toHttpStatus()` + thêm key i18n.
- **Không gọi `AppCode.getResponse()` / `getMessageLang()` ngoài thread của request** (vd trong `@Async`): chúng đọc `RequestContextHolder` (ThreadLocal request-scoped).
- **JWT**: chỉ **access token** (`type=access`) mới xác thực request; refresh token chỉ để gọi `/v1/auth/refresh-token`. Khi sinh token mới luôn đi qua `JwtProcessor` để giữ đúng claim `type`.
- **Logging**: dùng Log4j2 (`@Log4j2` của Lombok), KHÔNG dùng Logback. **Không log dữ liệu nhạy cảm** — request/response log đã đi qua `LogMasker`; nếu tự log thêm, đừng in `Authorization`, password, token thô.
- **Dependency injection**: ưu tiên constructor injection qua `@RequiredArgsConstructor` trên field `final`.
- **Validation**: dùng `@Valid` / `@Validated`; lỗi tự được `GlobalExceptionHandler` gom thành `{field: [messages]}` với HTTP 400 — không cần tự bắt.
- **Enum làm request param**: viết converter trong `converter/request` theo mẫu `StatusEnumConverter`/`ProductStatusEnumConverter` — `@Component implements Converter<String, E>`, giá trị không hợp lệ thì log + trả `null` (param coi như không truyền, KHÔNG ném lỗi 400 cho cả request).
- **Comment/mô tả tiếng Việt**, khớp văn phong các comment xung quanh.
- **Bố cục package theo concern**: `config/` (mỗi concern một subpackage), `controller/vN`, `service` + `service/impl`, `dto/app/{request,response}`, `enums`, `exception`, `filter`, `security`, `client`, `context`, `converter`, `utils`. Riêng module IAM là NGOẠI LỆ: nhóm theo domain thay vì theo version — subfolder `iam` NẰM TRONG từng layer (`controller/iam`, `service/impl/iam`, `entity/iam`, ...), không theo `controller/vN`. Thêm class IAM mới thì đặt đúng subfolder `iam` tương ứng, không đặt lẫn vào `controller/v1`.

### B-IAM. Module IAM/RBAC — bẫy dễ tái phạm khi sửa/mở rộng

- **`DynamicPermissionFilter` PHẢI được gắn tường minh** vào `SecurityConfig` qua `http.addFilterAfter(..., AuthorizationFilter.class)` + set `accessDeniedHandler` — KHÔNG để Spring Boot tự đăng ký filter này theo thứ tự mặc định (từng gây bug: bị từ chối trả về 401 thay vì 403 đúng ra phải có). Cũng phải tắt đường tự đăng ký trùng của chính filter đó (xem `SecurityConfig.dynamicPermissionFilterRegistration`, `setEnabled(false)`) nếu không mỗi request bị kiểm tra permission 2 lần.
- **`PUT` (update) của Role/Permission/Menu/User: field bị thiếu trong body = GIỮ NGUYÊN giá trị hiện có, KHÔNG fallback về default của lúc tạo mới.** Từng có bug: dùng chung 1 hàm `applyFields()` cho cả create lẫn update, khiến PUT thiếu field `status` vô tình bật lại `ACTIVE` một role/permission/menu đang `DISABLED`. Nếu thêm field mới có default lúc tạo, nhớ truyền `field hiện có của entity` làm fallback ở nhánh update, không phải hằng số default.
- **`@Builder.Default` KHÔNG có tác dụng khi Jackson deserialize JSON** (chỉ áp dụng khi dựng qua `builder()` trong code Java) — field nào muốn có default khi client bỏ trống PHẢI xử lý null-check tường minh ở tầng service, không dựa vào `@Builder.Default` trên DTO request.
- **Role của user KHÔNG được nhúng cứng vào JWT rồi tin mãi** — `/v1/auth/login` và `/v1/auth/refresh-token` đều phải đọc `dev_iam.user_role` MỚI NHẤT mỗi lần (không copy claim `roles` cũ khi refresh), nếu không khoá tài khoản (`enabled=false`) hoặc gỡ quyền không có tác dụng cho tới khi token tự hết hạn.
- **Token Keycloak: `preferred_username` không đủ tin cậy để định danh** — phải chốt bằng claim `sub` (UUID nội bộ, không đổi được) qua `app_user.keycloak_subject`, theo mô hình trust-on-first-use (xem `JwtAuthenticationServiceImpl.resolveKeycloakAuthorities`). Cũng phải check claim `typ=Bearer` trước khi tin token Keycloak — refresh token Keycloak ký bằng cùng key, thiếu check này thì refresh token dùng được như access token.
- **Chặn admin tự khoá chính mình**: tự xoá/tự disable tài khoản, hoặc tự gỡ hết role của chính mình qua `PUT /v1/rbac/users/{userId}/roles` đều phải bị chặn (400) — so username hiện tại (`SecurityContextHolder`) với username của bản ghi đang bị sửa.

### Gọi service ngoài (outbound HTTP)

- Viết client kế thừa `RestAbstractHttpClient` và override `defaultHeaders()`. KHÔNG tự `new RestTemplate`.
- **Tự động cho mọi client** (cấu hình ở `RestTemplateConfig`, `rest-template.*`): connection pool, các timeout, retry tầng transport (chỉ request idempotent), connection TTL, validate-after-inactivity, metrics pool.
- **Circuit breaker là OPT-IN** — KHÔNG tự bật. Phải truyền breaker vào constructor:
  - 1 upstream đơn giản: truyền bean `outboundHttpCircuitBreaker`.
  - **Nhiều upstream ⇒ MỖI upstream MỘT breaker riêng** (qua `CircuitBreakerRegistry`) để sự cố upstream này không làm mở mạch upstream khác:
    ```java
    @Component
    public class PaymentClient extends RestAbstractHttpClient {
        public PaymentClient(RestTemplate restTemplate, CircuitBreakerRegistry registry) {
            super(restTemplate, registry, "payment");   // breaker riêng tên "payment"
        }
        @Override protected HttpHeaders defaultHeaders() { /* ... */ }
    }
    ```
- Ngưỡng breaker (cửa sổ 20 / min 10 call / >50% lỗi / mở 10s) cấu hình TẬP TRUNG ở `resilience.yaml` khối `resilience4j.circuitbreaker` (KHÔNG hardcode trong code — `Resilience4jConfig` chỉ lấy bean theo tên). Breaker BỎ QUA lỗi HTTP upstream (4xx/5xx); chỉ lỗi kết nối/timeout mới mở mạch. Mạch mở → client trả `503` fail-fast.
- Bật log chi tiết outbound bằng cách để logger `LoggingInterceptor` ở DEBUG (mặc định tắt để khỏi buffer body ở prod).

### Giới hạn request đầu vào (inbound rate limiting)

- `RateLimitFilter` chặn khi một IP client gọi QUÁ NHIỀU — **giới hạn THEO IP**: mỗi IP một `RateLimiter` riêng (tạo lười, dựng từ config khuôn mẫu, giữ trong **cache Caffeine có giới hạn**), nên một IP "ngốn" hết quota không ảnh hưởng IP khác. Vượt ngưỡng → trả `429` (`AppCode.CODE_429`) ngay, không cho đi sâu vào xử lý. Chạy sau `RequestContextFilter`, trước Spring Security.
- **Mọi tùy chọn nằm ở `resilience.yaml`** (NƠI DUY NHẤT):
  - Ngưỡng số lượng (cho mỗi IP): `resilience4j.ratelimiter.configs.inbound` (`limit-for-period`, `limit-refresh-period`, `timeout-duration`) — override qua env `INBOUND_RATE_LIMIT` / `INBOUND_RATE_REFRESH` / `INBOUND_RATE_TIMEOUT`. Đặt dưới `configs` (không phải `instances`) vì đây là khuôn mẫu cho limiter tạo động theo IP.
  - Hành vi bộ lọc: `app.rate-limit` (`enabled`, `limiter-name`, `client-ip-header`, `max-clients`, `client-ttl`, `excluded-paths`). Tắt bằng `INBOUND_RATE_LIMIT_ENABLED=false` (khi đó filter KHÔNG được đăng ký).
  - Chống phình bộ nhớ: cache Caffeine giới hạn `max-clients` (số IP tối đa) và `client-ttl` (dọn IP nhàn rỗi) — env `INBOUND_RATE_MAX_CLIENTS` / `INBOUND_RATE_CLIENT_TTL`.
- **Lấy IP đúng khi sau proxy/LB**: set `client-ip-header` (env `INBOUND_RATE_IP_HEADER`, vd `X-Forwarded-For`) — CHỈ khi proxy đáng tin tự ghi header, nếu không client giả mạo IP để né giới hạn. Để trống = dùng `remoteAddr`.
- Path probe/swagger/api-docs đã nằm trong `excluded-paths` để không bị giới hạn. Thêm endpoint public cần loại trừ thì bổ sung vào đây.
- **Vai trò: chỉ là "lưới an toàn" CẤP INSTANCE (backstop)** — limiter in-memory per-instance, chạy N replica thì giới hạn thực ≈ N × ngưỡng. Giới hạn IP/chống DDoS chính đặt ở rìa (CDN/WAF/gateway/nginx `limit_req`); cần giới hạn toàn cục chính xác qua nhiều replica thì dùng store tập trung (Redis token bucket), không dùng bộ lọc này.

### Redis (cache + distributed lock) — OPT-IN

- Cấu hình ở file RIÊNG `profiles/<profile>/redis.yaml`, dùng **cấu hình gốc của Spring** `spring.data.redis.*` (KHÔNG khai báo lại qua `app.*`).
- **Mặc định TẮT** (`spring.data.redis.enabled=false`, env `REDIS_ENABLED`) để base build/test/chạy được khi chưa có Redis (RedissonClient kết nối ngay lúc khởi tạo). Bật = đặt `REDIS_ENABLED=true`. Health-check Redis của actuator bám theo cùng cờ để `/actuator/health` không DOWN khi tắt.
- **Standalone hay cluster tự suy ra**: có `spring.data.redis.cluster.nodes` → CLUSTER; không có → STANDALONE theo `host`/`port`. Connection factory do Spring Boot tự dựng (không viết factory custom).
- Bean (đều `@ConditionalOnProperty` theo cờ): `RedisConfig` (RedisTemplate JSON + `RedisCacheManager` `@Primary` tên `CacheManager.REDIS`, TTL mặc định từ `AppConfig`, TTL riêng theo enum `KeyCache`), `RedissonConfig` (`RedissonClient` single/cluster), service `RedisDistributedService`/`RedisDistributedLocker` (khóa phân tán). Boot 4: `RedisProperties` đổi tên thành `DataRedisProperties` (chuyển sang `org.springframework.boot.data.redis.autoconfigure`); value serializer là `GenericJacksonJsonRedisSerializer` của Jackson 3 (jsr310 có sẵn, không cần đăng ký `JavaTimeModule`).
- **Cache local Caffeine** (`CaffeineConfig`, LUÔN bật, tên `CacheManager.CAFFEINE`): map TTL theo `KeyCache` GIỐNG Redis. Là cache MẶC ĐỊNH khi tắt Redis; khi bật Redis thì Redis là primary, Caffeine vẫn dùng qua tên cho cache local nóng.
- Dùng cache: `@Cacheable(value = KeyCache.CacheName.XXX)` (mặc định/primary). Muốn ép cache local: thêm `cacheManager = CacheManager.CAFFEINE`. Thêm cache mới → thêm hằng vào `KeyCache` (TTL = `null` ⇒ dùng TTL mặc định); cả Redis lẫn Caffeine đều tự nhận.
- Khóa phân tán: inject `RedisDistributedService`, gọi `getDistributedLock(key)` rồi `tryLock(...)/unlock()`.
- `KeyCache` gốc để trống (skeleton) nhưng repo này ĐÃ thêm `ROLE_PERMISSIONS`/`ROLE_MENUS` (TTL 6h, phục vụ IAM) — thêm cache mới thì thêm hằng tiếp vào đây, đừng tạo tên cache rời. Test tích hợp `RedisIntegrationTest` dùng **Testcontainers** (Redis thật trong Docker; KHÔNG có Docker thì tự bỏ qua nên `mvnw test` vẫn xanh).

### B-infra. Infra bên thứ 3 (MySQL, MongoDB, RabbitMQ, Kafka) — OPT-IN, mặc định TẮT

> Đây là base **`-full`**. Base **`-lite`** KHÔNG có 4 infra này — đừng port chúng sang lite.

- Mỗi infra một file `profiles/<profile>/<tên>.yaml`, dùng **cấu hình gốc của Spring** (KHÔNG bọc qua `app.*`); mọi giá trị override được qua env, có default hợp lý. Mỗi file có khối **standalone** đầy đủ + khối **cluster/HA comment sẵn** (bỏ comment khi cần).
- **Mặc định TẮT mà base vẫn build/test/chạy**: client kết nối LƯỜI và base KHÔNG khai báo listener/entity nào. Health indicator gate theo cờ để `/actuator/health` UP khi tắt.
- Đường BẬT kiểm chứng bằng Testcontainers thật ở `src/test/java/com/ringme/base/infra/` (`mongo:7`/`rabbitmq:3.13-management-alpine`/`apache/kafka:3.8.1` — MySQL không còn test vì đã thay bằng Oracle), tự bỏ qua (`assumeTrue`) khi KHÔNG có Docker → `mvnw test` vẫn xanh.
- **MySQL — đã bị thay bằng Oracle, gần như không còn dùng** (xem CLAUDE.md mục "Oracle: two schemas, one datasource" cho datasource thật; `mysql.yaml` đã xoá khỏi mọi profile, `mysql-connector-j` comment trong `pom.xml`). Muốn khôi phục: viết lại `profiles/<env>/mysql.yaml`, bật lại dependency, và mỗi môi trường chỉ chọn MỘT trong MySQL/Oracle — cơ chế tắt của nó (loại `DataSourceAutoConfiguration` qua `spring.autoconfigure.exclude`) giết luôn datasource Oracle vì Boot chỉ tạo một bean `DataSource`.
- **MongoDB** (`spring-boot-starter-data-mongodb`): **Boot 4 ĐỔI prefix `spring.data.mongodb.*` → `spring.mongodb.*`** (`MongoProperties` về `org.springframework.boot.mongodb.autoconfigure`); dùng prefix cũ sẽ âm thầm fallback `localhost:27017`. `uri` PHẢI kèm tên database, không Boot 4 báo "Database name must not be empty". BẬT: `MONGODB_ENABLED=true` + `MONGODB_URI`. Health: `management.health.mongodb.enabled` (Boot 4 đổi `mongo`→`mongodb`).
- **RabbitMQ/AMQP** (`spring-boot-starter-amqp`): kết nối lười, base không có `@RabbitListener`. BẬT: `RABBITMQ_ENABLED=true` + host/creds. Cluster = `spring.rabbitmq.addresses`. Health: `management.health.rabbit.enabled`.
- **Kafka** (dùng **`spring-boot-kafka`**, KHÔNG phải `spring-kafka` trần): **Boot 4 chuyển `KafkaAutoConfiguration` ra module `spring-boot-kafka`** — chỉ thêm `spring-kafka` thì có class `KafkaTemplate` nhưng KHÔNG có bean tự cấu hình. `spring-boot-kafka` kéo `spring-kafka` theo. Serializer String mặc định (JSON/SASL_SSL comment sẵn). Kafka KHÔNG có health indicator → gate bằng quy ước `KAFKA_ENABLED`. BẬT: `KAFKA_BOOTSTRAP_SERVERS` (+ `admin.fail-fast:false`).
- **Thêm/bớt 1 file infra ⇒ cập nhật `spring.config.name` ở CẢ HAI nơi** (xem mục E).

---

## C. Bảo mật — không được làm hỏng

- Stateless, CSRF off, CORS theo `app.cors.allowed-origins`. Prod KHÔNG để CORS `*`.
- Mọi path không khai báo public đều yêu cầu xác thực (`anyRequest().authenticated()`).
- `/actuator/health/**` để public cho probe; các actuator endpoint khác giữ kín (chỉ phơi `health`).
- Method security bật (`@EnableMethodSecurity(jsr250Enabled = true)`): dùng `@RolesAllowed`. Bị từ chối → trả 403 (đã có handler).

---

## D. Đa ngôn ngữ (i18n)

- Message nằm ở `src/main/resources/i18n/`: `messages.properties` (mặc định = tiếng Anh, là fallback), `messages_vi.properties` (tiếng Việt). File lưu **UTF-8**.
- **Thêm mã `AppCode`/mã nghiệp vụ mới ⇒ phải thêm key vào CẢ `messages.properties` VÀ `messages_<lang>.properties`.** Thiếu thì message rơi về message cứng của enum (qua `getMessageOrDefault`), không lỗi nhưng mất bản dịch.
- Ngôn ngữ lấy từ header `language` (mặc định `en`). Không khớp ngôn ngữ → dùng bundle mặc định (`fallback-to-system-locale: false`).
- Format tham số (`{0}`, `{1}`) giao cho `MessageSource` qua `MultiLangManager.getMessage(key, lang, args...)`, không tự gọi `MessageFormat`.

---

## E. Cấu hình & profiles

> Cơ chế đầy đủ (danh sách file per-profile, vì sao `spring.config.name` phải liệt kê từng tên file, vì sao khối Consul phải nằm ở `application.yaml` gốc) xem CLAUDE.md mục **Configuration & profiles** — dưới đây chỉ là các quy tắc phải giữ khi sửa.

- **Thêm/bớt file trong `profiles/<profile>/` ⇒ cập nhật `spring.config.name` ở CẢ HAI nơi phải đồng bộ**: `BaseApplication.main()` (`setDefaultProperties`) và `maven-surefire-plugin` (`systemPropertyVariables` trong `pom.xml`). Thiếu nơi nào thì file mới âm thầm KHÔNG được nạp ở nơi đó (runtime hoặc test) — không có lỗi báo ra.
- Khối **Spring Cloud Consul** giữ nguyên ở `application.yaml` gốc (xử lý ở bootstrap, trước khi file profile được nạp), KHÔNG tách ra file profile. Mặc định TẮT (`CONSUL_ENABLED=false`); khi bật, discovery là opt-in riêng (`CONSUL_DISCOVERY_ENABLED=true`). Giá trị `spring.config.import` có dấu `:` cuối (vd `optional:consul:`) phải để trong nháy kép trong YAML.
- Thêm môi trường mới = tạo `resources/profiles/<tên>/` với cùng bộ file rồi chạy `SPRING_PROFILES_ACTIVE=<tên>` — KHÔNG sửa `application.yaml`.

---

## F. Build / test / chạy

- **Build bằng JDK 25** (`maven-enforcer-plugin` chặn `[25,26)` ở phase `validate` — đặt `JAVA_HOME` đúng). **KHÔNG xoá khối `<annotationProcessorPaths>` (Lombok) trong `maven-compiler-plugin`**: JDK 23+ không còn tự chạy annotation processor trên classpath, xoá là mọi thành phần Lombok sinh ra compile lỗi "cannot find symbol" (chi tiết: CLAUDE.md mục Commands).
- Lệnh:
  ```bash
  ./mvnw spring-boot:run          # chạy dev (http://localhost:8386/base)
  ./mvnw clean test               # chạy toàn bộ test
  ./mvnw clean package            # đóng gói exploded jar + lib/
  docker compose up --build -d    # build + chạy bằng Docker (mặc định profile dev)
  ```
- **Docker**: compose gốc KHÔNG publish port — điểm vào duy nhất là nginx dùng chung ở `E:/study/shared-nginx` qua network `shared-edge` (tạo 1 lần: `docker network create shared-edge`). Infra local có sẵn compose trong `docker/` (kafka, rabbitmq, mongodb, mariadb, redis-cluster, redis-sentinel, keycloak, postgres); riêng `docker/postgres` build image `base-postgres:16-vn-tz` để khôi phục alias timezone `Asia/Saigon` (postgres:16 đã bỏ, client gửi tên cũ sẽ bị từ chối kết nối).
- **Definition of Done** cho mọi thay đổi:
  1. Thêm/sửa tính năng phải kèm test.
  2. `./mvnw clean test` xanh (JDK 25) trước khi báo hoàn thành — dẫn chứng bằng kết quả thật, không phỏng đoán.
  3. Mã/exception/status mới → cập nhật `AppCode`, `GlobalExceptionHandler`, i18n đồng bộ.
  4. Comment/tài liệu mới bằng tiếng Việt.
  5. Thêm/sửa endpoint → export lại `postman/base-api-openapi.json` từ `/v3/api-docs` (file này KHÔNG tự đồng bộ).
