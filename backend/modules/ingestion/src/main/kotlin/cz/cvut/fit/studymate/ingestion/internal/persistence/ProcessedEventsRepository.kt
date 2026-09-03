package cz.cvut.fit.studymate.ingestion.internal.persistence

import java.util.UUID

/**
 * Idempotency boundary for each ingestion Kafka stage.
 * An id is recorded only after the stage's side effect succeeds or it has
 * terminally marked the material as failed.
 */
internal interface ProcessedEventsRepository {
    fun exists(eventId: UUID): Boolean

    fun markProcessed(eventId: UUID)
}
