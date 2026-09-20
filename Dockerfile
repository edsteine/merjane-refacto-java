# Build the application first with: task build
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY api/target/springboilerplate-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
