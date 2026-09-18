FROM maven:3.9.6-eclipse-temurin-17 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
EXPOSE 8080
COPY --from=build target/spaceHub.jar spaceHub.jar
ENTRYPOINT ["java","-jar","/spaceHub.jar"]