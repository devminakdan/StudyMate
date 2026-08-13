package cz.cvut.fit.studymate.course.internal.exception

class QuotaExceededException(
    val usedBytes: Long,
    val limitBytes: Long,
    val requestedBytes: Long,
) : RuntimeException(
    "Storage quota exceeded: $usedBytes used + $requestedBytes requested would exceed the limit of $limitBytes bytes"
)
