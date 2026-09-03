package cz.cvut.fit.studymate.ingestion.internal.kafka.event

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Consumer-owned representation of the material-uploaded JSON event.
 *
 * This intentionally remains independent from the course module's Kotlin event
 * class: Kafka messages are a JSON contract, rather than a shared JVM contract.
 */
data class MaterialUploadedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val materialId: UUID,
    val courseId: UUID,
    val ownerId: UUID,
    val storagePath: String,
    val mimeType: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
