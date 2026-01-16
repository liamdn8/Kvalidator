# Multi-stage Dockerfile for KValidator
# Stage 1: Build React frontend
FROM node:18-alpine AS frontend-builder

WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: Build Java backend with Quarkus
FROM maven:3.8-openjdk-11 AS backend-builder

WORKDIR /app
COPY pom.xml ./
COPY src ./src

# Copy built frontend to backend resources
COPY --from=frontend-builder /frontend/dist ./src/main/resources/META-INF/resources/kvalidator/web/

# Build backend
RUN mvn clean package -DskipTests

# Stage 3: Runtime
FROM eclipse-temurin:11-jre-alpine

WORKDIR /app

# Install curl for healthcheck
RUN apk add --no-cache curl

# Create user and group with ID 1000
RUN addgroup -g 1000 kvalidator && \
    adduser -D -u 1000 -G kvalidator kvalidator && \
    mkdir -p /home/kvalidator && \
    chown -R kvalidator:kvalidator /home/kvalidator

# Copy the built application
COPY --from=backend-builder /app/target/quarkus-app/ ./

# Copy validation config (can be overridden by ConfigMap in k8s)
COPY --from=backend-builder /app/src/main/resources/validation-config.yaml ./config/validation-config.yaml

# Create directory for results and set ownership
RUN mkdir -p /tmp/.kvalidator/results && \
    chown -R kvalidator:kvalidator /app /tmp/.kvalidator

# Switch to non-root user
USER kvalidator

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/q/health || exit 1

# Run the application
CMD ["java", "-jar", "quarkus-run.jar"]
