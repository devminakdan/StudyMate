package cz.cvut.fit.studymate.storage.api

class StorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class FileNotFoundInStorageException(ref: StorageRef) : RuntimeException("File not found: ${ref.path}")
