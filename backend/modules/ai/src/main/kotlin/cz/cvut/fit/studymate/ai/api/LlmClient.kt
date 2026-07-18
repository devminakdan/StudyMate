package cz.cvut.fit.studymate.ai.api

interface LlmClient {
    suspend fun complete(messages: List<ChatMessage>, options: CompletionOptions = CompletionOptions()): String
}
