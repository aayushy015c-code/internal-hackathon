# Step 1: build the jar with Maven (this image has Java + Maven)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Step 2: a small image with only Java to run it (no Maven, no source code)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# don't run as root
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/core-api.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=docker \
    SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/api/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
