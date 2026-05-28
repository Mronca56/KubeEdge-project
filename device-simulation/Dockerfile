FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/device-simulation/target/*-jar-with-dependencies.jar ./simulator.jar
CMD ["java", "-jar", "simulator.jar"]