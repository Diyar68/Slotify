# syntax=docker/dockerfile:1

# 1. Build React from source. No local node_modules or dist are used.
FROM node:24-bookworm-slim AS frontend-build
WORKDIR /build/frontend
COPY slotify-frontend/package.json slotify-frontend/package-lock.json ./
RUN npm ci
COPY slotify-frontend/ ./
RUN npm run build

# 2. Build the executable JAR, including the React build and existing tests.
FROM eclipse-temurin:21-jdk-noble AS backend-build
WORKDIR /build/backend
COPY slotify-backend/mvnw ./mvnw
COPY slotify-backend/.mvn/ ./.mvn/
COPY slotify-backend/pom.xml ./pom.xml
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B dependency:go-offline
COPY slotify-backend/src/ ./src/
COPY --from=frontend-build /build/frontend/dist/ ./src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B package \
    && jar tf target/slotify-backend-0.0.1-SNAPSHOT.jar > /tmp/jar-contents \
    && grep -q 'BOOT-INF/classes/static/index.html' /tmp/jar-contents \
    && grep -q 'BOOT-INF/classes/static/assets/.*\.js$' /tmp/jar-contents \
    && grep -q 'BOOT-INF/classes/static/assets/.*\.css$' /tmp/jar-contents \
    && grep -q 'BOOT-INF/classes/static/favicon.svg' /tmp/jar-contents

# 3. Only the JRE and application are needed at runtime.
FROM eclipse-temurin:21-jre-noble AS runtime
WORKDIR /app
RUN groupadd --gid 10001 slotify \
    && useradd --uid 10001 --gid slotify --no-create-home --shell /usr/sbin/nologin slotify
COPY --from=backend-build --chown=slotify:slotify /build/backend/target/slotify-backend-0.0.1-SNAPSHOT.jar ./slotify.jar
USER 10001:10001
EXPOSE 8080
# No profile or credentials are baked in. exec forwards termination signals to Java.
ENTRYPOINT ["sh", "-c", "exec java -jar /app/slotify.jar --server.port=\"${PORT:-8080}\" --server.address=0.0.0.0"]
