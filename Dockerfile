# Stage 1: Build the application using Maven (Java 21)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Stage 2: Run the application (Java 21)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Required environment variables (set in your hosting platform — e.g. Render dashboard):
#   DB_URL          — JDBC connection string for Supabase PostgreSQL
#   DB_USERNAME     — Database username
#   DB_PASSWORD     — Database password
#   VAPID_PUBLIC_KEY  — VAPID EC public key (base64url)
#   VAPID_PRIVATE_KEY — VAPID EC private key (base64url)
#   VAPID_SUBJECT     — mailto: address for VAPID JWT sub claim
#   PORT              — (optional) server port, defaults to 8081
ENTRYPOINT ["java", "-jar", "app.jar"]