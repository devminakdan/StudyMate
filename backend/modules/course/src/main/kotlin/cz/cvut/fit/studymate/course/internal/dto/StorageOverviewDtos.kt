package cz.cvut.fit.studymate.course.internal.dto

import cz.cvut.fit.studymate.course.api.Material
import java.time.OffsetDateTime
import java.util.UUID

internal data class StorageOverviewResponse(
    val totalUsedBytes: Long,
    val limitBytes: Long,
    val courses: List<CourseStorageInfo>,
)

internal data class CourseStorageInfo(
    val courseId: UUID,
    val courseName: String,
    val totalSizeBytes: Long,
    val materialCount: Int,
    val lastUsedAt: OffsetDateTime?,
    val materials: List<MaterialStorageInfo>,
)

internal data class MaterialStorageInfo(
    val materialId: UUID,
    val filename: String,
    val sizeBytes: Long,
    val uploadedAt: OffsetDateTime,
)

internal fun Material.toStorageInfo() = MaterialStorageInfo(
    materialId = id,
    filename = originalFilename,
    sizeBytes = sizeBytes,
    uploadedAt = uploadedAt,
)
