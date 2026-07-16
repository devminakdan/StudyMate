package cz.cvut.fit.studymate.storage.api

import java.io.InputStream

interface StorageService {
    fun store(path: String, content: InputStream): StorageRef
    fun retrieve(path: StorageRef): InputStream
    fun delete(path: StorageRef)
    fun exists(path: StorageRef): Boolean
}
