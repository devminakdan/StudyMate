package cz.cvut.fit.studymate.course.internal.repository

import cz.cvut.fit.studymate.course.api.Material
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.generated.tables.references.COURSES
import cz.cvut.fit.studymate.course.generated.tables.references.MATERIALS
import org.jooq.DSLContext
import org.jooq.impl.DSL.exists
import org.jooq.impl.DSL.selectOne
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
internal class MaterialRepository(
    private val dsl: DSLContext
) {
    fun create(courseId: UUID, originalFilename: String, storagePath: String, mimeType: String, sizeBytes: Long): Material {
        return dsl.insertInto(MATERIALS)
            .set(MATERIALS.COURSE_ID, courseId)
            .set(MATERIALS.ORIGINAL_FILENAME, originalFilename)
            .set(MATERIALS.STORAGE_PATH, storagePath)
            .set(MATERIALS.MIME_TYPE, mimeType)
            .set(MATERIALS.SIZE_BYTES, sizeBytes)
            .returning()
            .fetchOneInto(Material::class.java)!!
    }
    fun findById(id: UUID): Material? {
        return dsl.selectFrom(MATERIALS)
            .where(MATERIALS.ID.eq(id))
            .fetchOneInto(Material::class.java)
    }
    fun existsOwnedCourse(courseId: UUID, ownerId: UUID): Boolean {
        return dsl.fetchExists(
            COURSES,
            COURSES.ID.eq(courseId).and(COURSES.OWNER_ID.eq(ownerId)),
        )
    }
    fun findByIdInOwnedCourse(id: UUID, courseId: UUID, ownerId: UUID): Material? {
        return dsl.selectFrom(MATERIALS)
            .where(MATERIALS.ID.eq(id))
            .and(MATERIALS.COURSE_ID.eq(courseId))
            .and(ownedCourseExists(ownerId))
            .fetchOneInto(Material::class.java)
    }
    fun findByCourseIdWithPagination(courseId: UUID, ownerId: UUID, limit: Int, offset: Int): List<Material> {
        return dsl.selectFrom(MATERIALS)
            .where(MATERIALS.COURSE_ID.eq(courseId))
            .and(ownedCourseExists(ownerId))
            .orderBy(MATERIALS.UPLOADED_AT.desc())
            .limit(limit)
            .offset(offset)
            .fetchInto(Material::class.java)
    }
    fun findByCourseIdWithPagination(courseId: UUID, limit: Int, offset: Int): List<Material> {
        return dsl.selectFrom(MATERIALS)
            .where(MATERIALS.COURSE_ID.eq(courseId))
            .orderBy(MATERIALS.UPLOADED_AT.desc())
            .limit(limit)
            .offset(offset)
            .fetchInto(Material::class.java)
    }
    fun findAllByCourseId(courseId: UUID): List<Material> {
        return dsl.selectFrom(MATERIALS)
            .where(MATERIALS.COURSE_ID.eq(courseId))
            .orderBy(MATERIALS.UPLOADED_AT.desc())
            .fetchInto(Material::class.java)
    }
    fun countByCourseId(courseId: UUID): Int {
        return dsl.fetchCount(MATERIALS, MATERIALS.COURSE_ID.eq(courseId))
    }
    fun countByCourseId(courseId: UUID, ownerId: UUID): Int {
        return dsl.fetchCount(
            MATERIALS,
            MATERIALS.COURSE_ID.eq(courseId).and(ownedCourseExists(ownerId)),
        )
    }
    fun updateStatus(id: UUID, status: MaterialStatus, pageCount: Int? = null, error: String? = null): Material? {
        val processedAt = if (status == MaterialStatus.READY || status == MaterialStatus.FAILED) OffsetDateTime.now() else null

        return dsl.update(MATERIALS)
            .set(MATERIALS.STATUS, status.name)
            .set(MATERIALS.PAGE_COUNT, pageCount)
            .set(MATERIALS.ERROR_MESSAGE, error)
            .set(MATERIALS.PROCESSED_AT, processedAt)
            .where(MATERIALS.ID.eq(id))
            .returning()
            .fetchOneInto(Material::class.java)
    }
    fun delete(id: UUID) {
        dsl.deleteFrom(MATERIALS)
            .where(MATERIALS.ID.eq(id))
            .execute()
    }
    fun deleteFromOwnedCourse(id: UUID, courseId: UUID, ownerId: UUID): Boolean {
        return dsl.deleteFrom(MATERIALS)
            .where(MATERIALS.ID.eq(id))
            .and(MATERIALS.COURSE_ID.eq(courseId))
            .and(ownedCourseExists(ownerId))
            .execute() == 1
    }

    private fun ownedCourseExists(ownerId: UUID) =
        exists(
            selectOne()
                .from(COURSES)
                .where(COURSES.ID.eq(MATERIALS.COURSE_ID))
                .and(COURSES.OWNER_ID.eq(ownerId)),
        )
}
