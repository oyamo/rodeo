FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache git ca-certificates \
    && addgroup -S rodeo \
    && adduser -S -G rodeo -h /home/rodeo rodeo
COPY --from=build /build/target/rodeo-0.1.0-SNAPSHOT.jar /app/rodeo.jar
USER rodeo
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/rodeo.jar"]
CMD ["--rodeo.config=/etc/rodeo/config.toml"]
