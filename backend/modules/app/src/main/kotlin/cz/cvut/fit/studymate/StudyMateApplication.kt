package cz.cvut.fit.studymate

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StudyMateApplication

fun main(args: Array<String>) {
    runApplication<StudyMateApplication>(*args)
}
