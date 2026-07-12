package cz.cvut.fit.studymate.iam.internal.service

import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.api.UserLookup
import cz.cvut.fit.studymate.iam.internal.exception.UserNotFoundException
import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class UserService(
    private val repository: UserRepository
) : UserLookup {
    override fun findById(id: UUID): User? {
        return repository.findById(id) ?: throw UserNotFoundException(id)
    }

    override fun findByIds(ids: Collection<UUID>): Map<UUID, User> {
        return repository.findByIds(ids)
    }
}
