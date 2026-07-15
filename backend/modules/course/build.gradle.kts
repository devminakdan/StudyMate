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
    // TODO: add back once :storage module exists

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.liquibase:liquibase-core")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.apache.tika:tika-core:2.9.2")
    implementation("org.apache.tika:tika-parsers-standard-package:2.9.2")

    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.52")

    jooqGenerator("org.postgresql:postgresql:42.7.4")

    liquibaseRuntime("org.liquibase:liquibase-core:4.27.0")
    liquibaseRuntime("org.postgresql:postgresql:42.7.4")
    liquibaseRuntime("info.picocli:picocli:4.7.5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

jooq {
    configurations {
        create("main") {
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5432/studymate"
                    user = "postgres"
                    password = "postgres"
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        includes = "courses|materials"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isFluentSetters = true
                        isPojos = true
                    }
                    target.apply {
                        packageName = "cz.cvut.fit.studymate.course.generated"
                        directory = "${project.projectDir}/src/main/kotlin-generated"
                    }
                }
            }
        }
    }
}

sourceSets {
    main {
        kotlin {
            srcDir("src/main/kotlin-generated")
        }
    }
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
        "--changelog-file=db/changelog/course-changelog.yml",
        "--url=jdbc:postgresql://localhost:5432/studymate",
        "--username=postgres",
        "--password=postgres",
        "update",
    )
    // courses.owner_id has a real FK on users(id), and databasechangelog/
    // databasechangeloglock are shared, database-wide tables — running this
    // concurrently with :iam:liquibaseUpdate on an empty database is both
    // logically wrong (FK target missing) and racy (two CREATE TABLE
    // databasechangelog statements at once). Gradle won't infer this
    // ordering on its own since these are independent JavaExec tasks.
    dependsOn(":iam:liquibaseUpdate")
}

tasks.named("generateJooq") {
    dependsOn("liquibaseUpdate")
}
