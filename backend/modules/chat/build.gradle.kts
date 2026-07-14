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

val liquibaseRuntime by configurations.creating

dependencies {
    api(project(":common"))
    implementation(project(":iam"))
    implementation(project(":course"))
    implementation(project(":retrieval"))
    implementation(project(":ai"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.liquibase:liquibase-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")

    jooqGenerator("org.postgresql:postgresql:42.7.4")

    liquibaseRuntime("org.liquibase:liquibase-core:4.27.0")
    liquibaseRuntime("org.postgresql:postgresql:42.7.4")
    liquibaseRuntime("info.picocli:picocli:4.7.5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

tasks.register<JavaExec>("liquibaseUpdate") {
    group = "liquibase"
    description = "Applies pending Liquibase changesets directly, without starting the Spring Boot app."
    mainClass.set("liquibase.integration.commandline.LiquibaseCommandLine")
    classpath = configurations["liquibaseRuntime"]
    args = listOf(
        "--changelog-file=src/main/resources/db/changelog/chat-changelog.yml",
        "--url=jdbc:postgresql://localhost:5432/studymate",
        "--username=postgres",
        "--password=postgres",
        "update",
    )
}
