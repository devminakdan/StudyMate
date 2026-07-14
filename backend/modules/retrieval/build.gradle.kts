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
    implementation(project(":ai"))

    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.liquibase:liquibase-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    runtimeOnly("org.postgresql:postgresql")

    implementation("com.pgvector:pgvector:0.1.6")

    jooqGenerator("org.postgresql:postgresql:42.7.4")

    liquibaseRuntime("org.liquibase:liquibase-core:4.27.0")
    liquibaseRuntime("org.postgresql:postgresql:42.7.4")
    liquibaseRuntime("info.picocli:picocli:4.7.5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.12")
}

tasks.register<JavaExec>("liquibaseUpdate") {
    group = "liquibase"
    description = "Applies pending Liquibase changesets directly, without starting the Spring Boot app."
    mainClass.set("liquibase.integration.commandline.LiquibaseCommandLine")
    classpath = configurations["liquibaseRuntime"]
    // workingDir + a src/main/resources-relative changelog path so the
    // recorded changeset filenames match what Spring Boot's own
    // classpath:-based SpringLiquibase bean records at app startup — see
    // the :iam:liquibaseUpdate task for the full explanation.
    workingDir = file("src/main/resources")
    args = listOf(
        "--changelog-file=db/changelog/retrieval-changelog.yml",
        "--url=jdbc:postgresql://localhost:5432/studymate",
        "--username=postgres",
        "--password=postgres",
        "update",
    )
}
