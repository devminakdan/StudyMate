package cz.cvut.fit.studymate.course.internal.exception

import java.util.UUID

class MaterialNotFoundException(id: UUID) : RuntimeException("Material not found: $id")
