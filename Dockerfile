# Runtime image for the jar produced by the Jenkins "Build Application" stage (mvn package).
# The jar is built outside Docker, so this only needs a JRE.
FROM eclipse-temurin:21-jre

WORKDIR /app

# Run as an unprivileged user
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring

ARG JAR_FILE=target/*.jar
COPY --chown=spring:spring ${JAR_FILE} app.jar

USER spring:spring

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
