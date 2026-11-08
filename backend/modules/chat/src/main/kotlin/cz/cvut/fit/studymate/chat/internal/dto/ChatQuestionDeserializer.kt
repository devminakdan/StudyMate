package cz.cvut.fit.studymate.chat.internal.dto

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

internal class ChatQuestionDeserializer : JsonDeserializer<String>() {

    override fun deserialize(parser: JsonParser, context: DeserializationContext): String {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(String::class.java, "Chat question must be a JSON string")
        }
        return parser.text
    }
}
