package cz.cvut.fit.studymate.ai.api

data class QuizSourceMaterial(val title: String, val content: String)

object QuizPrompts {

    private const val DEFAULT_QUESTION_COUNT = 5

    fun buildSystemPrompt(questionCount: Int = DEFAULT_QUESTION_COUNT): String = """
        You write exam-preparation quizzes for university students based on
        their own course materials.

        Generate exactly $questionCount multiple-choice questions using only
        the course materials provided below. Each question must have exactly
        4 answer options with exactly one correct answer.

        Respond with ONLY a JSON array, no other text, in this exact shape:
        [
          {
            "question": "...",
            "options": ["...", "...", "...", "..."],
            "correctOptionIndex": 0
          }
        ]
    """.trimIndent()

    fun buildUserMessage(materials: List<QuizSourceMaterial>): String {
        val materialsText = materials.joinToString(separator = "\n\n") { material ->
            "[${material.title}]\n${material.content}"
        }
        return """
            Course materials:

            $materialsText
        """.trimIndent()
    }

    fun buildMessages(
        materials: List<QuizSourceMaterial>,
        questionCount: Int = DEFAULT_QUESTION_COUNT,
    ): List<ChatMessage> = listOf(
        ChatMessage(MessageRole.SYSTEM, buildSystemPrompt(questionCount)),
        ChatMessage(MessageRole.USER, buildUserMessage(materials)),
    )
}
