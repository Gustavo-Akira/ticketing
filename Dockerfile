FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system ticketing && useradd --system --gid ticketing ticketing
COPY --from=build /workspace/build/libs/core-0.0.1-SNAPSHOT.jar app.jar
USER ticketing
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
