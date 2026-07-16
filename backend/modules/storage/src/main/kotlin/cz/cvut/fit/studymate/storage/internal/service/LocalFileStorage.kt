package cz.cvut.fit.studymate.storage.internal.service

import cz.cvut.fit.studymate.storage.api.FileNotFoundInStorageException
import cz.cvut.fit.studymate.storage.api.StorageRef
import cz.cvut.fit.studymate.storage.api.StorageService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

@Component
@ConditionalOnProperty(name = ["studymate.storage.provider"], havingValue = "local", matchIfMissing = true)
internal class LocalFileStorage(
    @Value("\${studymate.storage.local.base-path}") basePath: String
): StorageService {
    private val baseDir: Path = Paths.get(basePath).toAbsolutePath().normalize()

    init { Files.createDirectories(baseDir) }

    override fun store(path: String, content: InputStream): StorageRef {
        val target = baseDir.resolve(path).normalize()
        require(target.startsWith(baseDir)) { "Invalid path: potential traversal attack" }
        Files.createDirectories(target.parent)
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING)
        return StorageRef(path)
    }

    override fun retrieve(ref: StorageRef): InputStream {
        val resolved = baseDir.resolve(ref.path).normalize()
        require(resolved.startsWith(baseDir)) { "Invalid path: potential traversal attack" }
        if (!Files.exists(resolved)) throw FileNotFoundInStorageException(ref)
        return Files.newInputStream(resolved)
    }

    override fun delete(ref: StorageRef) {
        val resolved = baseDir.resolve(ref.path).normalize()
        require(resolved.startsWith(baseDir)) { "Invalid path: potential traversal attack" }
        Files.deleteIfExists(resolved)
    }

    override fun exists(ref: StorageRef): Boolean {
        val resolved = baseDir.resolve(ref.path).normalize()
        return resolved.startsWith(baseDir) && Files.exists(resolved)
    }
}
