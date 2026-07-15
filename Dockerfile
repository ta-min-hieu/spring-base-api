# syntax=docker/dockerfile:1

# ---- Giai đoạn build: dùng Maven + JDK 25 (đúng phiên bản maven-enforcer yêu cầu) ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
# Bỏ test khi đóng gói image (test đã chạy ở CI); enforcer vẫn chặn JDK sai ở phase validate.
RUN chmod +x mvnw && ./mvnw -B clean package -DskipTests

# ---- Giai đoạn runtime: chỉ cần JRE 25 ----
FROM eclipse-temurin:25-jre
WORKDIR /app
# Không chạy container bằng root — tạo user/group riêng (UID/GID cố định để khớp được với quyền thư
# mục host khi bind-mount ./logs, ./storage trong docker-compose.yml; nếu host tạo sẵn 2 thư mục đó
# với owner khác, cần chown lại trên host hoặc đổi UID này cho khớp).
RUN groupadd --gid 1001 base && useradd --uid 1001 --gid base --no-create-home --shell /usr/sbin/nologin base
# Dự án đóng gói dạng exploded jar: app.jar đi kèm thư mục lib/ (manifest Class-Path trỏ tới lib/).
COPY --from=build --chown=base:base /app/target/base-0.0.1.jar app.jar
COPY --from=build --chown=base:base /app/target/lib lib
USER base
# server.port mặc định 8386, context-path /base.
EXPOSE 8386
# Mặc định chạy profile dev. Production: truyền -e SPRING_PROFILES_ACTIVE=prod cùng các secret (xem .env.example).
ENTRYPOINT ["java", "-jar", "app.jar"]
