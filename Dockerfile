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
# Dự án đóng gói dạng exploded jar: app.jar đi kèm thư mục lib/ (manifest Class-Path trỏ tới lib/).
COPY --from=build /app/target/base-0.0.1.jar app.jar
COPY --from=build /app/target/lib lib
# server.port mặc định 8386, context-path /base.
EXPOSE 8386
# Mặc định chạy profile dev. Production: truyền -e SPRING_PROFILES_ACTIVE=prod cùng các secret (xem .env.example).
ENTRYPOINT ["java", "-jar", "app.jar"]
