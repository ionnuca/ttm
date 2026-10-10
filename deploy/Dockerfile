# Imaginea de producție: doar Java runtime și JAR-ul construit deja de GitHub Actions
# (mvn -Pproduction package). Construită pentru amd64 și arm64, deci merge pe orice VPS.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system ttm && useradd --system --gid ttm --home /app ttm
COPY app.jar /app/app.jar
USER ttm

ENV JAVA_OPTS="-Xmx1g"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
