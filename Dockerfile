FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh ./mvnw -B -ntp -DskipTests dependency:go-offline
COPY src/ src/
COPY frontend/ frontend/
RUN sh ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S davamly && adduser -S davamly -G davamly
COPY --from=build /app/target/davamly.jar /app/app.jar
USER davamly
ENV SPRING_PROFILES_ACTIVE=production \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s \
    CMD wget -q -O /dev/null "http://127.0.0.1:${PORT:-8080}/api/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
