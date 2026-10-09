# Reproducible runner image: the suite executes identically on a laptop, a Jenkins agent,
# an ECS task or an Azure Container App.
FROM maven:3.9-eclipse-temurin-17 AS deps
WORKDIR /suite
COPY pom.xml .
# Warm the dependency layer so day-to-day builds do not re-download the world.
RUN mvn -B -q dependency:go-offline

FROM deps AS runner
COPY testng-*.xml ./
COPY src ./src
COPY postman ./postman

ENV TEST_ENV=qa \
    BROWSER_HEADLESS=true \
    GRID_ENABLED=true \
    GRID_URL=http://selenium-hub:4444/wd/hub

ENTRYPOINT ["mvn", "-B", "test"]
CMD ["-Dsuite=testng-smoke.xml"]
