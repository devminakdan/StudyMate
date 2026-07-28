package cz.cvut.fit.studymate.course.internal.service

import cz.cvut.fit.studymate.course.api.Course
import cz.cvut.fit.studymate.course.api.CourseLookup
import cz.cvut.fit.studymate.course.api.Material
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.api.MaterialUploadedEvent
import cz.cvut.fit.studymate.course.internal.exception.CourseAccessDeniedException
import cz.cvut.fit.studymate.course.internal.exception.CourseNotFoundException
import cz.cvut.fit.studymate.course.internal.exception.InvalidMaterialException
import cz.cvut.fit.studymate.course.internal.exception.MaterialNotFoundException
import cz.cvut.fit.studymate.course.internal.repository.MaterialRepository
import cz.cvut.fit.studymate.storage.api.StorageRef
import cz.cvut.fit.studymate.storage.api.StorageService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.mock.web.MockMultipartFile
import java.time.OffsetDateTime
import java.util.UUID

internal class MaterialServiceTest {

    private val materialRepository = mockk<MaterialRepository>()
    private val courseLookup = mockk<CourseLookup>()
    private val storageService = mockk<StorageService>()
    private val kafkaTemplate = mockk<KafkaTemplate<String, MaterialUploadedEvent>>()
    private val service = MaterialService(materialRepository, courseLookup, storageService, kafkaTemplate)

    private fun course(
        id: UUID = UUID.randomUUID(),
        ownerId: UUID = UUID.randomUUID(),
    ) = Course(id, ownerId, "Programování a algoritmizace 2", "BI-PA2.21", null, OffsetDateTime.now(), OffsetDateTime.now())

    private fun material(
        id: UUID = UUID.randomUUID(),
        courseId: UUID = UUID.randomUUID(),
        storagePath: String = "materials/$courseId/generated.pdf",
    ) = Material(
        id, courseId, "lecture.pdf", storagePath, "application/pdf",
        1024L, MaterialStatus.PENDING, null, OffsetDateTime.now(), null, null
    )

    private fun pdfFile(name: String = "lecture.pdf") =
        MockMultipartFile("file", name, "application/pdf", "%PDF-1.4\n%some pdf content".toByteArray())

    // ---- uploadMaterial ----

    @Test
    fun `uploadMaterial throws CourseNotFoundException when the course does not exist`() {
        val courseId = UUID.randomUUID()
        every { courseLookup.findCourseById(courseId) } returns null

        assertThrows<CourseNotFoundException> {
            service.uploadMaterial(courseId, UUID.randomUUID(), pdfFile())
        }
    }

    @Test
    fun `uploadMaterial throws CourseAccessDeniedException when the caller isn't the owner`() {
        val existing = course()
        every { courseLookup.findCourseById(existing.id) } returns existing

        assertThrows<CourseAccessDeniedException> {
            service.uploadMaterial(existing.id, UUID.randomUUID(), pdfFile())
        }
    }

    @Test
    fun `uploadMaterial throws InvalidMaterialException for an empty file`() {
        val existing = course()
        every { courseLookup.findCourseById(existing.id) } returns existing
        val emptyFile = MockMultipartFile("file", "empty.pdf", "application/pdf", ByteArray(0))

        assertThrows<InvalidMaterialException> {
            service.uploadMaterial(existing.id, existing.ownerId, emptyFile)
        }
    }

    @Test
    fun `uploadMaterial throws InvalidMaterialException for a file over the size limit`() {
        val existing = course()
        every { courseLookup.findCourseById(existing.id) } returns existing
        val bigFile = MockMultipartFile("file", "big.pdf", "application/pdf", ByteArray(21 * 1024 * 1024))

        assertThrows<InvalidMaterialException> {
            service.uploadMaterial(existing.id, existing.ownerId, bigFile)
        }
    }

    @Test
    fun `uploadMaterial throws InvalidMaterialException for an unsupported file type`() {
        val existing = course()
        every { courseLookup.findCourseById(existing.id) } returns existing
        val textFile = MockMultipartFile("file", "notes.txt", "text/plain", "just plain text".toByteArray())

        assertThrows<InvalidMaterialException> {
            service.uploadMaterial(existing.id, existing.ownerId, textFile)
        }
    }

    @Test
    fun `uploadMaterial stores the file, persists the record, and publishes a Kafka event`() {
        val existing = course()
        val created = material(courseId = existing.id)
        every { courseLookup.findCourseById(existing.id) } returns existing
        every { storageService.store(any(), any()) } returns StorageRef(created.storagePath)
        every {
            materialRepository.create(existing.id, "lecture.pdf", created.storagePath, "application/pdf", any())
        } returns created
        every { kafkaTemplate.send(any<String>(), any(), any()) } returns mockk()

        val result = service.uploadMaterial(existing.id, existing.ownerId, pdfFile())

        assertThat(result).isEqualTo(created)
        verify(exactly = 1) { kafkaTemplate.send("studymate.material.uploaded", created.id.toString(), any()) }
    }

    @Test
    fun `uploadMaterial stores the file under the structured user_courseId path scheme`() {
        val existing = course()
        val created = material(courseId = existing.id)
        val pathSlot = slot<String>()
        every { courseLookup.findCourseById(existing.id) } returns existing
        every { storageService.store(capture(pathSlot), any()) } returns StorageRef(created.storagePath)
        every { materialRepository.create(existing.id, "lecture.pdf", created.storagePath, "application/pdf", any()) } returns created
        every { kafkaTemplate.send(any<String>(), any(), any()) } returns mockk()

        service.uploadMaterial(existing.id, existing.ownerId, pdfFile())

        assertThat(pathSlot.captured).startsWith("user_${existing.ownerId}/course_${existing.id}/")
        assertThat(pathSlot.captured).endsWith("_lecture.pdf")
    }

    // ---- listMaterials / countMaterials ----

    @Test
    fun `listMaterials translates page and size into limit and offset when calling the repository`() {
        val existing = course()
        val materials = listOf(material(courseId = existing.id))
        every { courseLookup.findCourseById(existing.id) } returns existing
        every { materialRepository.findByCourseIdWithPagination(existing.id, limit = 10, offset = 20) } returns materials

        val result = service.listMaterials(existing.id, existing.ownerId, page = 2, size = 10)

        assertThat(result).isEqualTo(materials)
    }

    @Test
    fun `listMaterials throws CourseAccessDeniedException when the caller isn't the owner`() {
        val existing = course()
        every { courseLookup.findCourseById(existing.id) } returns existing

        assertThrows<CourseAccessDeniedException> {
            service.listMaterials(existing.id, UUID.randomUUID(), page = 0, size = 10)
        }
    }

    @Test
    fun `countMaterials delegates to the repository`() {
        val existing = course()
        every { courseLookup.findCourseById(existing.id) } returns existing
        every { materialRepository.countByCourseId(existing.id) } returns 3

        val result = service.countMaterials(existing.id, existing.ownerId)

        assertThat(result).isEqualTo(3)
    }

    // ---- getMaterial ----

    @Test
    fun `getMaterial returns the material when it belongs to the course`() {
        val existing = course()
        val material = material(courseId = existing.id)
        every { courseLookup.findCourseById(existing.id) } returns existing
        every { materialRepository.findById(material.id) } returns material

        val result = service.getMaterial(existing.id, material.id, existing.ownerId)

        assertThat(result).isEqualTo(material)
    }

    @Test
    fun `getMaterial throws MaterialNotFoundException when no material exists with that id`() {
        val existing = course()
        val materialId = UUID.randomUUID()
        every { courseLookup.findCourseById(existing.id) } returns existing
        every { materialRepository.findById(materialId) } returns null

        assertThrows<MaterialNotFoundException> {
            service.getMaterial(existing.id, materialId, existing.ownerId)
        }
    }

    @Test
    fun `getMaterial throws MaterialNotFoundException when the material belongs to a different course`() {
        val existing = course()
        val otherCoursesMaterial = material(courseId = UUID.randomUUID())
        every { courseLookup.findCourseById(existing.id) } returns existing
        every { materialRepository.findById(otherCoursesMaterial.id) } returns otherCoursesMaterial

        assertThrows<MaterialNotFoundException> {
            service.getMaterial(existing.id, otherCoursesMaterial.id, existing.ownerId)
        }
    }

    // ---- deleteMaterial ----

    @Test
    fun `deleteMaterial deletes the stored file and the record`() {
        val existing = course()
        val material = material(courseId = existing.id)
        every { courseLookup.findCourseById(existing.id) } returns existing
        every { materialRepository.findById(material.id) } returns material
        every { storageService.delete(StorageRef(material.storagePath)) } just Runs
        every { materialRepository.delete(material.id) } just Runs

        service.deleteMaterial(existing.id, material.id, existing.ownerId)

        verify(exactly = 1) { storageService.delete(StorageRef(material.storagePath)) }
        verify(exactly = 1) { materialRepository.delete(material.id) }
    }

    @Test
    fun `deleteMaterial throws MaterialNotFoundException without deleting when the material belongs to a different course`() {
        val existing = course()
        val otherCoursesMaterial = material(courseId = UUID.randomUUID())
        every { courseLookup.findCourseById(existing.id) } returns existing
        every { materialRepository.findById(otherCoursesMaterial.id) } returns otherCoursesMaterial

        assertThrows<MaterialNotFoundException> {
            service.deleteMaterial(existing.id, otherCoursesMaterial.id, existing.ownerId)
        }

        verify(exactly = 0) { materialRepository.delete(any()) }
        verify(exactly = 0) { storageService.delete(any()) }
    }

    // ---- updateStatus ----

    @Test
    fun `updateStatus delegates to the repository`() {
        val materialId = UUID.randomUUID()
        every { materialRepository.updateStatus(materialId, MaterialStatus.READY, 10, null) } returns material(id = materialId)

        service.updateStatus(materialId, MaterialStatus.READY, 10, null)

        verify(exactly = 1) { materialRepository.updateStatus(materialId, MaterialStatus.READY, 10, null) }
    }
}
