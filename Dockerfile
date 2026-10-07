FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline
COPY src src
# Tests run in CI (mvn verify); the image build only needs the jar.
RUN mvn -q -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
# No package installs here: apt mirrors are often unreachable on restricted networks.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Plain-bash HTTP probe, so the image does not need curl or wget.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080; printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3; grep -q "\"status\":\"UP\"" <&3'
ENTRYPOINT ["java","-jar","/app/app.jar"]
