# Use JDK 17 as specified in your pom.xml
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app
# Copy the executable JAR of your sample application
COPY target/sample_app-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]