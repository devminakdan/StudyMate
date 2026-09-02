package cz.cvut.fit.studymate.course.internal.service

import cz.cvut.fit.studymate.course.internal.dto.CourseStorageInfo
import cz.cvut.fit.studymate.course.internal.dto.StorageOverviewResponse
import cz.cvut.fit.studymate.course.internal.dto.toStorageInfo
import cz.cvut.fit.studymate.course.internal.repository.CourseRepository
import cz.cvut.fit.studymate.course.internal.repository.MaterialRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class StorageOverviewService(
    private val courseRepository: CourseRepository,
    private val materialRepository: MaterialRepository,
) {
    fun getStorageOverview(userId: UUID): StorageOverviewResponse {
        val courses = courseRepository.findAllByOwnerIdOrderedByLastUsed(userId)

        val courseInfos = courses.map { course ->
            val materials = materialRepository.findAllByCourseId(course.id)
            CourseStorageInfo(
                courseId = course.id,
                courseName = course.name,
                totalSizeBytes = materials.sumOf { it.sizeBytes },
                materialCount = materials.size,
                lastUsedAt = course.lastUsedAt,
                materials = materials.map { it.toStorageInfo() },
            )
        }

        return StorageOverviewResponse(
            totalUsedBytes = courseInfos.sumOf { it.totalSizeBytes },
            limitBytes = StorageQuota.FREE_TIER_BYTES,
            courses = courseInfos,
        )
    }
}
