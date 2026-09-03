package cz.cvut.fit.studymate.ingestion.internal.persistence

import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * PostgreSQL-backed idempotency store. The primary key and `ON CONFLICT DO
 * NOTHING` make the final write safe when duplicate deliveries race.
 */
@Repository
internal class JooqProcessedEventsRepository(
    private val dsl: DSLContext,
) : ProcessedEventsRepository {
    override fun exists(eventId: UUID): Boolean = dsl.fetchExists(
        dsl.selectOne()
            .from(PROCESSED_EVENTS)
            .where(EVENT_ID.eq(eventId)),
    )

    override fun markProcessed(eventId: UUID) {
        dsl.insertInto(PROCESSED_EVENTS)
            .columns(EVENT_ID)
            .values(eventId)
            .onConflictDoNothing()
            .execute()
    }

    private companion object {
        val PROCESSED_EVENTS = table(name("ingestion_processed_events"))
        val EVENT_ID = field(name("event_id"), UUID::class.java)
    }
}
