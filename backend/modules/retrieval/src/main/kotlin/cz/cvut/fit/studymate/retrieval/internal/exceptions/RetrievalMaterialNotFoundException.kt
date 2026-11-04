package cz.cvut.fit.studymate.retrieval.internal.exceptions

import java.util.UUID

class RetrievalMaterialNotFoundException(materialId: UUID) :
    RuntimeException("Material $materialId was not found")
