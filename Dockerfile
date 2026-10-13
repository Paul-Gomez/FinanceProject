FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# La aplicación no necesita privilegios: corre con un usuario sin permisos de root.
RUN addgroup -S fincore && adduser -S fincore -G fincore
COPY --from=build /app/target/*.jar app.jar
USER fincore
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
