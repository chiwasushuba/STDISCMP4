FROM node:24.15.0-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/index.html frontend/vite.config.js ./
COPY frontend/src src
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY contracts contracts
COPY service-common service-common
COPY identity-service identity-service
COPY course-service course-service
COPY grade-service grade-service
COPY api-orchestrator api-orchestrator
COPY web-app web-app
COPY --from=frontend-build /frontend/dist frontend/dist
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --gid 10001 portal && useradd --uid 10001 --gid portal --no-create-home portal
ARG MODULE
COPY --from=build /workspace/${MODULE}/target/${MODULE}-1.0.0.jar /app/app.jar
USER portal
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
