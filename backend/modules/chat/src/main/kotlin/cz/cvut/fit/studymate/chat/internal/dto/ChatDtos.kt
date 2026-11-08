package cz.cvut.fit.studymate.chat.internal.dto

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import jakarta.validation.constraints.NotBlank
import java.util.UUID

internal data class ChatRequest(
    @field:JsonDeserialize(using = ChatQuestionDeserializer::class)
    @field:NotBlank(message = "Chat question must not be blank")
    val question: String,
)

internal data class ChatResponse(
    val answer: String,
    val sources: List<ChatSource>,
)

internal data class ChatSource(
    val source: String,
    val chunkId: UUID,
    val materialId: UUID,
    val chunkIndex: Int,
    val text: String,
)
