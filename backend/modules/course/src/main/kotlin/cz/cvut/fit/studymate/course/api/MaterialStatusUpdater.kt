package cz.cvut.fit.studymate.course.api

import java.util.UUID

interface MaterialStatusUpdater {
    fun updateStatus(materialId: UUID, status: MaterialStatus, pageCount: Int? = null, error: String? = null)
}
