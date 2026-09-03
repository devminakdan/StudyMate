package cz.cvut.fit.studymate.ingestion.internal.notification

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * Broadcasts material-status changes and maintains a short-lived status snapshot
 * for clients that reconnect after a Pub/Sub notification.
 */
@Component
internal class StatusEventRelay(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    fun relay(ownerId: UUID, event: StatusEvent) {
        redisTemplate.convertAndSend(
            "material-status:user:$ownerId",
            objectMapper.writeValueAsString(event),
        )

        val snapshotKey = "material:status:snapshot:${event.materialId}"
        val snapshot = redisTemplate.opsForHash<String, String>()
        snapshot.put(snapshotKey, "status", event.status.name)
        event.error?.let { snapshot.put(snapshotKey, "error", it) }
        redisTemplate.expire(snapshotKey, SNAPSHOT_TTL)
    }

    private companion object {
        val SNAPSHOT_TTL: Duration = Duration.ofHours(24)
    }
}
