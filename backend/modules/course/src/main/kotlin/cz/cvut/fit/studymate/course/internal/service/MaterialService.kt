package cz.cvut.fit.studymate.course.internal.service

import cz.cvut.fit.studymate.course.api.Course
import cz.cvut.fit.studymate.course.api.CourseLookup
import cz.cvut.fit.studymate.course.api.Material
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.api.MaterialStatusUpdater
import cz.cvut.fit.studymate.course.api.MaterialUploadedEvent
import cz.cvut.fit.studymate.course.internal.exception.CourseAccessDeniedException
import cz.cvut.fit.studymate.course.internal.exception.CourseNotFoundException
import cz.cvut.fit.studymate.course.internal.exception.InvalidMaterialException
import cz.cvut.fit.studymate.course.internal.exception.MaterialNotFoundException
import cz.cvut.fit.studymate.course.internal.repository.MaterialRepository
import cz.cvut.fit.studymate.storage.api.StorageRef
import cz.cvut.fit.studymate.storage.api.StorageService
import org.apache.tika.Tika
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.util.UUID

@Service
internal class MaterialService(
    private val materialRepository: MaterialRepository,
    private val courseLookup: CourseLookup,
    private val storageService: StorageService,
    private val kafkaTemplate: KafkaTemplate<String, MaterialUploadedEvent>
) : MaterialStatusUpdater {
    companion object {
        private const val MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024L // 20MB
        private val ALLOWED_MIME_TYPES = setOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
        private const val MATERIAL_UPLOADED_TOPIC = "studymate.material.uploaded"
    }
    private val tika = Tika()

    private fun requireOwnership(courseId: UUID, userId : UUID) : Course {
        val course = courseLookup.findCourseById(courseId) ?: throw CourseNotFoundException(courseId)
        if(course.ownerId != userId) throw CourseAccessDeniedException("You don't own course $courseId")
        return course
    }

    private fun requireMaterialInCourse(courseId: UUID, materialId: UUID): Material {
        val material = materialRepository.findById(materialId) ?: throw MaterialNotFoundException(materialId)
        if (material.courseId != courseId) throw MaterialNotFoundException(materialId)
        return material
    }

    private fun buildStoragePath(userId: UUID, courseId: UUID, originalFilename: String): String {
        val sanitized = sanitizeFilename(originalFilename)
        return "user_${userId}/course_${courseId}/${UUID.randomUUID()}_${sanitized}"
    }

    private fun sanitizeFilename(filename: String): String {
        return filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    fun uploadMaterial(courseId: UUID, userId: UUID, file: MultipartFile) : Material {
        requireOwnership(courseId, userId)
        if (file.isEmpty) throw InvalidMaterialException("File is empty")
        if (file.size > MAX_FILE_SIZE_BYTES) throw InvalidMaterialException("File exceeds the maximum allowed size of ${MAX_FILE_SIZE_BYTES / (1024 * 1024)}MB")
        val originalFilename = file.originalFilename ?: throw InvalidMaterialException("File must have a name")

        val bytes = file.bytes
        val detectedType = tika.detect(bytes)
        if (detectedType !in ALLOWED_MIME_TYPES) {
            throw InvalidMaterialException("Unsupported file type: $detectedType")
        }

        val path = buildStoragePath(userId, courseId, originalFilename)
        val ref = storageService.store(path, ByteArrayInputStream(bytes))

        val created = materialRepository.create(
            courseId = courseId,
            originalFilename = originalFilename,
            storagePath = ref.path,
            mimeType = detectedType,
            sizeBytes = file.size,
        )

        val event = MaterialUploadedEvent(
            materialId = created.id,
            courseId = courseId,
            ownerId = userId,
            storagePath = created.storagePath,
            mimeType = detectedType,
        )
        kafkaTemplate.send(MATERIAL_UPLOADED_TOPIC, created.id.toString(), event)

        return created
    }

    fun listMaterials(courseId: UUID, userId: UUID, page: Int, size: Int): List<Material> {
        requireOwnership(courseId, userId)
        return materialRepository.findByCourseIdWithPagination(courseId, limit = size, offset = page * size)
    }

    fun countMaterials(courseId: UUID, userId: UUID): Int {
        requireOwnership(courseId, userId)
        return materialRepository.countByCourseId(courseId)
    }

    fun getMaterial(courseId: UUID, materialId: UUID, userId: UUID): Material {
        requireOwnership(courseId, userId)
        return requireMaterialInCourse(courseId, materialId)
    }

    fun deleteMaterial(courseId: UUID, materialId: UUID, userId: UUID) {
        requireOwnership(courseId, userId)
        val material = requireMaterialInCourse(courseId, materialId)
        storageService.delete(StorageRef(material.storagePath))
        materialRepository.delete(materialId)
    }

    override fun updateStatus(materialId: UUID, status: MaterialStatus, pageCount: Int?, error: String?) {
        materialRepository.updateStatus(materialId, status, pageCount, error)
    }
}

