plugins {
  java
  id("org.springframework.boot") version "4.0.3"
  id("io.spring.dependency-management") version "1.1.7"
  jacoco
}

group = "com.back"
version = "0.0.1-SNAPSHOT"
description = "coach-backend"

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
  }
}

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}

repositories {
  mavenCentral()
}

dependencies {
  // Spring Boot Core
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-json")
  implementation("org.springframework.boot:spring-boot-starter-security")

  // OAuth2
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

  // JWT
  implementation("io.jsonwebtoken:jjwt-api:0.13.0")
  runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
  runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

  // Database
  implementation("org.springframework.boot:spring-boot-flyway")
  implementation("org.flywaydb:flyway-core")
  implementation("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  // Redis
  implementation("org.springframework.boot:spring-boot-starter-data-redis")

  // GLM (Zhipu) LLM
  implementation("ai.z.openapi:zai-sdk:0.3.3")

  // JSON Schema Validation (LLM 응답 검증)
  implementation("com.networknt:json-schema-validator:1.5.6")

  // API Docs
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

  // Monitoring
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("io.micrometer:micrometer-registry-prometheus")

  // Lombok
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")

  // Dev
  developmentOnly("org.springframework.boot:spring-boot-devtools")
  developmentOnly("com.h2database:h2")
  implementation("org.springframework.boot:spring-boot-h2console")

  // Test
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
  testImplementation("org.springframework.boot:spring-boot-starter-web")
  testImplementation("org.springframework.boot:spring-boot-testcontainers")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.testcontainers:postgresql:1.20.6")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation("org.wiremock:wiremock-standalone:3.12.1")
}

tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

jacoco {
  toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
  reports {
    xml.required.set(true)
    html.required.set(true)
    csv.required.set(false)
  }
}
