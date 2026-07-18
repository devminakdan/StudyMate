package cz.cvut.fit.studymate.ai.api

data class ContextChunk(val source: String, val content: String)

object RagChatPrompts {

    val SYSTEM_PROMPT = """
        You are a study assistant answering a student's question using only
        the course material excerpts provided below.

        Rules:
        - Answer strictly using the provided context. Do not use outside
          knowledge, and do not invent facts that are not present in it.
        - If the context does not contain enough information to answer,
          say so plainly instead of guessing.
        - Cite the source of every claim you make by naming the excerpt it
          came from.
    """.trimIndent()

    fun buildUserMessage(chunks: List<ContextChunk>, question: String): String {
        val context = chunks.joinToString(separator = "\n\n") { chunk ->
            "[Source: ${chunk.source}]\n${chunk.content}"
        }
        return """
            Context from course materials:

            $context

            Student's question: $question
        """.trimIndent()
    }

    fun buildMessages(chunks: List<ContextChunk>, question: String): List<ChatMessage> = listOf(
        ChatMessage(MessageRole.SYSTEM, SYSTEM_PROMPT),
        ChatMessage(MessageRole.USER, buildUserMessage(chunks, question)),
    )
}
