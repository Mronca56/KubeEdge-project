FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

WORKDIR /build

COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/central-cloud/target/*.jar application.jar
ENTRYPOINT ["java","-jar","application.jar"]