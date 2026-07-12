plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
    id("nu.studer.jooq")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.4")
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
    }
}

dependencies {
    api(project(":common"))
    implementation(project(":iam"))
    implementation(project(":course"))
    implementation(project(":retrieval"))
    implementation(project(":ai"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.liquibase:liquibase-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")

    jooqGenerator("org.postgresql:postgresql:42.7.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.12")
}
