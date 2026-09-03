package cz.cvut.fit.studymate.ingestion.internal.chunking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SentenceAwareChunkerTest {
    private val chunker = SentenceAwareChunker()

    @Test
    fun `returns no chunks for blank input`() {
        assertThat(chunker.chunk("")).isEmpty()
        assertThat(chunker.chunk(" \n\t ")).isEmpty()
    }

    @Test
    fun `keeps complete sentences and normalizes whitespace between them`() {
        val chunks = chunker.chunk("First sentence.\n  Second sentence!\tIs this the third sentence?")

        assertThat(chunks)
            .extracting({ it.chunkIndex }, { it.text })
            .containsExactly(
            org.assertj.core.api.Assertions.tuple(
                0,
                "First sentence. Second sentence! Is this the third sentence?",
            ),
        )
    }

    @Test
    fun `uses trailing complete sentences as overlap when target size is reached`() {
        val sentences = (1..7).map { number ->
            "Sentence $number ${"x".repeat(180)}."
        }

        val chunks = chunker.chunk(sentences.joinToString(" "))

        assertThat(chunks.map { it.chunkIndex }).containsExactly(0, 1)
        assertThat(chunks.map { it.text }).containsExactly(
            sentences.subList(0, 4).joinToString(" "),
            sentences.subList(3, 7).joinToString(" "),
        )
        assertThat(chunks[0].text).contains(sentences[3])
        assertThat(chunks[1].text).startsWith(sentences[3])
    }

    @Test
    fun `does not split a sentence that is longer than the target size`() {
        val longSentence = "x".repeat(850) + "."

        val chunks = chunker.chunk("$longSentence A short second sentence.")

        assertThat(chunks.map { it.chunkIndex }).containsExactly(0, 1)
        assertThat(chunks.map { it.text }).containsExactly(longSentence, "A short second sentence.")
    }
}
