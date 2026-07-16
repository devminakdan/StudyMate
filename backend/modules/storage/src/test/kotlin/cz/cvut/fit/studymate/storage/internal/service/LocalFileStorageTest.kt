package cz.cvut.fit.studymate.storage.internal.service

import cz.cvut.fit.studymate.storage.api.FileNotFoundInStorageException
import cz.cvut.fit.studymate.storage.api.StorageRef
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LocalFileStorageTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: LocalFileStorage

    @BeforeEach
    fun setup(){
        storage = LocalFileStorage(tempDir.toString())
    }

    @Test
    fun `store return the ref for content that equal to content, that retrieves for this ref`(){
        val ref = storage.store("file.txt", "hello".byteInputStream())
        assertThat(storage.retrieve(ref).readBytes()).isEqualTo("hello".toByteArray())
    }

    @Test
    fun `retrieve of non exists file throws`(){
        assertThrows<FileNotFoundInStorageException> { (storage.retrieve(StorageRef("nope.txt"))) }
    }

    @Test
    fun `delete exists file and then exists() method returns false`(){
        val ref = storage.store("file.txt", "hello".byteInputStream())

        assertThat(storage.exists(ref)).isTrue

        storage.delete(ref)

        assertThat(storage.exists(ref)).isFalse
    }

    @Test
    fun `store two same files will return two different references`(){
        val ref1 = storage.store("a.txt", "one".byteInputStream())
        val ref2 = storage.store("b.txt", "two".byteInputStream())
        assertThat(storage.retrieve(ref1).readBytes()).isEqualTo("one".toByteArray())
        assertThat(storage.retrieve(ref2).readBytes()).isEqualTo("two".toByteArray())
    }

    @Test
    fun `path traversal attack refuses by method`(){
        assertThrows<IllegalArgumentException> { storage.store("../outside.txt", "x".byteInputStream()) }
    }

    @Test
    fun `nested directories in path will be created`(){
        storage.store("a/b/c/file.txt", "x".byteInputStream())
        assertThat(Files.exists(tempDir.resolve("a/b/c/file.txt"))).isTrue()
    }

}
