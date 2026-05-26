FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /device-simulation

ENV BROKER_URL="tcp://broker.hivemq.com:1883"

COPY --from=build /build/target/simulator-jar-with-dependencies.jar ./simulator.jar

CMD ["java", "-jar", "simulator.jar"]