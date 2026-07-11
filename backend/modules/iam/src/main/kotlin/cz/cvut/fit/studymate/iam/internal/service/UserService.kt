package cz.cvut.fit.studymate.iam.internal.service

import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.api.UserLookup
import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class UserService(
    private val repository: UserRepository
) : UserLookup {
    override fun findById(id: UUID): User? {
        TODO("Not yet implemented")
    }

    override fun findByIds(ids: Collection<UUID>): Map<UUID, User> {
        TODO("Not yet implemented")
    }
}
