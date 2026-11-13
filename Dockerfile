# --- Etapa 1: compilar (imagen con JDK completo; no llega a la imagen final) ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Primero solo lo necesario para bajar dependencias: Docker cachea esta capa mientras el pom no cambie
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline

COPY src src
# Las pruebas se ejecutan con ./mvnw test; aqui solo se empaqueta
RUN ./mvnw -q -B -DskipTests package

# --- Etapa 2: ejecutar (solo JRE: imagen mas liviana y con menos superficie de ataque) ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# No correr como root dentro del contenedor
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/target/polizas-*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
