package cz.cvut.fit.studymate.course.api

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** Kafka topic names supplied by the host application's configuration. */
@Component
@ConfigurationProperties(prefix = "studymate.kafka.topics")
class KafkaTopicsProperties {
    lateinit var materialUploaded: String
    lateinit var textExtracted: String
    lateinit var textChunked: String
    lateinit var chunksEmbedded: String
}
