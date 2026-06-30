rootProject.name = "studymate-backend"

include(
    ":common",
    ":iam",
    ":course",
    ":storage",
    ":ai",
    ":retrieval",
    ":ingestion",
    ":chat",
    ":quiz",
    ":app",
)

rootProject.children.forEach { module ->
    module.projectDir = file("modules/${module.name}")
}
