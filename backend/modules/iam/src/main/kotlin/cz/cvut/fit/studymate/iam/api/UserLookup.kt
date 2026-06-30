package cz.cvut.fit.studymate.iam.api

import java.util.UUID

interface UserLookup {
    fun findById(id: UUID): User?
    fun findByIds(ids: Collection<UUID>): Map<UUID, User>
}
