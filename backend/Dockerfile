FROM eclipse-temurin:21-jdk-alpine AS build
ARG MAVEN_MIRROR_URL=https://repo.maven.apache.org/maven2
ENV MVNW_REPOURL=${MAVEN_MIRROR_URL}
WORKDIR /workspace
RUN mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>build-mirror</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/leave-management-*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
