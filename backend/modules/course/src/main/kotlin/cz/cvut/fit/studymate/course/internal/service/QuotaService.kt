package cz.cvut.fit.studymate.course.internal.service

import cz.cvut.fit.studymate.course.generated.tables.references.COURSES
import cz.cvut.fit.studymate.course.generated.tables.references.MATERIALS
import cz.cvut.fit.studymate.course.internal.exception.QuotaExceededException
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class QuotaService(
    private val dsl: DSLContext
) {
    fun getUserStorageUsage(userId: UUID): Long =
        dsl.select(DSL.sum(MATERIALS.SIZE_BYTES))
            .from(MATERIALS)
            .join(COURSES).on(MATERIALS.COURSE_ID.eq(COURSES.ID))
            .where(COURSES.OWNER_ID.eq(userId))
            .fetchOne(0, Long::class.java) ?: 0L

    fun checkQuota(userId: UUID, incomingFileSize: Long) {
        val current = getUserStorageUsage(userId)
        if (current + incomingFileSize > StorageQuota.FREE_TIER_BYTES) {
            throw QuotaExceededException(current, StorageQuota.FREE_TIER_BYTES, incomingFileSize)
        }
    }
}
