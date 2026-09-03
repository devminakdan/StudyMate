package cz.cvut.fit.studymate.ingestion.internal.chunking

import cz.cvut.fit.studymate.ingestion.internal.kafka.event.ChunkDto
import org.springframework.stereotype.Component
import java.text.BreakIterator
import java.util.Locale

/**
 * Forms approximately 800-character chunks while preserving sentence boundaries.
 * The next chunk starts with complete trailing sentences from the previous chunk,
 * providing an approximately 15% (120 character) overlap when possible.
 */
@Component
internal class SentenceAwareChunker {
    private companion object {
        const val TARGET_CHUNK_SIZE = 800
        const val OVERLAP_SIZE = 120
    }

    fun chunk(text: String): List<ChunkDto> {
        val sentences = extractSentences(text)
        if (sentences.isEmpty()) return emptyList()

        val chunks = mutableListOf<ChunkDto>()
        var start = 0

        while (start < sentences.size) {
            var end = start
            var length = 0

            while (end < sentences.size) {
                val separatorLength = if (end == start) 0 else 1
                val candidateLength = length + separatorLength + sentences[end].length

                // A sentence is indivisible. A single unusually long sentence is
                // therefore emitted whole instead of being cut in the middle.
                if (end > start && candidateLength > TARGET_CHUNK_SIZE) break

                length = candidateLength
                end++
            }

            chunks += ChunkDto(
                chunkIndex = chunks.size,
                text = sentences.subList(start, end).joinToString(" "),
            )

            if (end == sentences.size) break

            val nextStart = overlapStart(sentences, start, end)
            // If a chunk contains one sentence, overlap would otherwise prevent
            // forward progress. There is no whole-sentence overlap in that case.
            start = if (nextStart == start) end else nextStart
        }

        return chunks
    }

    private fun extractSentences(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        val iterator = BreakIterator.getSentenceInstance(Locale.ROOT)
        iterator.setText(text)
        val sentences = mutableListOf<String>()
        var begin = iterator.first()
        var end = iterator.next()

        while (end != BreakIterator.DONE) {
            text.substring(begin, end)
                .trim()
                .takeIf(String::isNotEmpty)
                ?.let(sentences::add)
            begin = end
            end = iterator.next()
        }

        return sentences
    }

    private fun overlapStart(sentences: List<String>, chunkStart: Int, chunkEnd: Int): Int {
        var overlapLength = 0
        var index = chunkEnd

        while (index > chunkStart && overlapLength < OVERLAP_SIZE) {
            index--
            overlapLength += sentences[index].length + 1
        }

        return index
    }
}
