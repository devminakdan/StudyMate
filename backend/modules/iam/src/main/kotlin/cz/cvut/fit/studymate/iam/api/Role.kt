package cz.cvut.fit.studymate.iam.api

enum class Role {
    USER,
    ADMIN;

    fun asAuthority(): String = "ROLE_$name"
}
