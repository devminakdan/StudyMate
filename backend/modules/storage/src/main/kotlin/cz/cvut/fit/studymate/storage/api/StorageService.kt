package cz.cvut.fit.studymate.storage.api

import java.io.InputStream

interface StorageService {
    fun store(path: String, content: InputStream): StorageRef
    fun retrieve(ref: StorageRef): InputStream
    fun delete(ref: StorageRef)
    fun exists(ref: StorageRef): Boolean
}
