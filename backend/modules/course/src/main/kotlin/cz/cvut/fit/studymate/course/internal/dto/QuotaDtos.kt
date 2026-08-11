package cz.cvut.fit.studymate.course.internal.dto

internal data class QuotaResponse(
    val usedBytes: Long,
    val limitBytes: Long,
)
