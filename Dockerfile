# STAGE 1: Build the JAR using Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# STAGE 2: Run the JAR using Azul Zulu
FROM azul/zulu-openjdk-alpine:17-jre-latest
WORKDIR /app
# Copy the JAR from the 'build' stage instead of your local disk
# Replace your current COPY line with this:
COPY --from=build /app/target/varista-service*.jar varista-service.jar

EXPOSE 8080
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
ENTRYPOINT ["java", "-jar", "varista-service.jar"]
