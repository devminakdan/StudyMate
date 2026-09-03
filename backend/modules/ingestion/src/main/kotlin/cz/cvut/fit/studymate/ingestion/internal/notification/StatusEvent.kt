package cz.cvut.fit.studymate.ingestion.internal.notification

import cz.cvut.fit.studymate.course.api.MaterialStatus
import java.util.UUID

/**
 * The material-status payload delivered to the material owner's Redis channel.
 */
data class StatusEvent(
    val materialId: UUID,
    val status: MaterialStatus,
    val error: String? = null,
)
