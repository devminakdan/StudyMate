package cz.cvut.fit.studymate.course.internal.repository

import cz.cvut.fit.studymate.course.api.Course
import cz.cvut.fit.studymate.course.generated.tables.references.COURSES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
internal class CourseRepository(
    private val dsl: DSLContext
) {
    fun create(
        ownerId: UUID,
        name: String,
        code: String?,
        description: String?
    ) : Course {
        return dsl.insertInto(COURSES)
            .set(COURSES.OWNER_ID, ownerId)
            .set(COURSES.NAME, name)
            .set(COURSES.CODE, code)
            .set(COURSES.DESCRIPTION, description)
            .returning()
            .fetchOneInto(Course::class.java)!!
    }

    fun findById(id: UUID): Course? {
        return dsl.selectFrom(COURSES)
            .where(COURSES.ID.eq(id))
            .fetchOneInto(Course::class.java)
    }

    fun findByOwnerId(ownerId: UUID, limit : Int, offset: Int) : List<Course> {
        return dsl.selectFrom(COURSES)
            .where(COURSES.OWNER_ID.eq(ownerId))
            .orderBy(COURSES.CREATED_AT.desc())
            .limit(limit)
            .offset(offset)
            .fetchInto(Course::class.java)
    }

    fun countByOwnerId(ownerId: UUID): Int =
        dsl.fetchCount(COURSES, COURSES.OWNER_ID.eq(ownerId))

    fun updateOwned(id: UUID, ownerId: UUID, name: String, code: String?, description: String?) : Course? {
        val now = OffsetDateTime.now()

        return dsl.update(COURSES)
            .set(COURSES.NAME, name)
            .set(COURSES.CODE, code)
            .set(COURSES.DESCRIPTION, description)
            .set(COURSES.UPDATED_AT, now)
            .where(COURSES.ID.eq(id))
            .and(COURSES.OWNER_ID.eq(ownerId))
            .returning()
            .fetchOneInto(Course::class.java)
    }

    fun deleteOwned(id: UUID, ownerId: UUID): Boolean {
        return dsl.deleteFrom(COURSES)
            .where(COURSES.ID.eq(id))
            .and(COURSES.OWNER_ID.eq(ownerId))
            .execute() == 1
    }

    fun updateLastUsedAt(courseId: UUID, timestamp: OffsetDateTime) {
        dsl.update(COURSES)
            .set(COURSES.LAST_USED_AT, timestamp)
            .where(COURSES.ID.eq(courseId))
            .execute()
    }

    fun findAllByOwnerIdOrderedByLastUsed(ownerId: UUID): List<Course> {
        return dsl.selectFrom(COURSES)
            .where(COURSES.OWNER_ID.eq(ownerId))
            .orderBy(COURSES.LAST_USED_AT.asc().nullsFirst())
            .fetchInto(Course::class.java)
    }
}
