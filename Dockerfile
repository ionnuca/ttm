# ---------- Etapa 1: build (Maven + build-ul de producție Vaadin) ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependențele se descarcă separat, ca să fie refolosite din cache la build-urile următoare
COPY pom.xml .
RUN mvn -B -q -Pproduction dependency:go-offline || true

COPY src ./src
RUN mvn -B -Pproduction -DskipTests package \
    && cp target/ttm-*.jar /build/app.jar

# ---------- Etapa 2: imaginea finală, doar cu Java runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system ttm && useradd --system --gid ttm --home /app ttm
COPY --from=build /build/app.jar /app/app.jar
USER ttm

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
