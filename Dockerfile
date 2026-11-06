# STAGE 1: Build the JAR using Maven
FROM maven:3.9.11-eclipse-temurin-24 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# STAGE 2: Run the JAR using Azul Zulu
FROM azul/zulu-openjdk-alpine:24-jre-latest
WORKDIR /app
# Copy the JAR from the 'build' stage instead of your local disk
# Replace your current COPY line with this:
COPY --from=build /app/target/varista-service*.jar varista-service.jar

EXPOSE 8080
RUN addgroup -S spring && adduser -S spring -G spring
RUN mkdir -p /app/data/invoice /app/data/quotation && chown -R spring:spring /app/data
USER spring:spring
ENTRYPOINT ["java", "-jar", "varista-service.jar"]
