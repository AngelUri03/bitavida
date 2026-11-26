package com.bitavida.app.data.education

import com.bitavida.app.domain.education.EducationTopic
import com.bitavida.app.domain.education.NaiveBayesParameters

object EducationModelCodec {
    fun decode(text: String): NaiveBayesParameters {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.size >= 4 && lines[0] == "BITAVIDA_MNB\t3\t0.1") { "Unsupported education model" }
        val labels = lines[1].split('\t')
        require(labels.last() == "__UNKNOWN__")
        val topics = labels.dropLast(1).map(EducationTopic::valueOf)
        val expected = if (topics.all { it.group == com.bitavida.app.domain.education.EducationGroup.REMINDERS }) {
            EducationTopic.entries.filter { it.group == com.bitavida.app.domain.education.EducationGroup.REMINDERS }
        } else {
            EducationTopic.entries.filter { it.group != com.bitavida.app.domain.education.EducationGroup.REMINDERS }
        }
        require(topics.toSet() == expected.toSet() && topics.size == expected.size)
        val documents = lines[2].split('\t').map(String::toInt)
        require(documents.size == labels.size && documents.all { it > 0 })
        val counts = linkedMapOf<String, List<Int>>()
        for (line in lines.drop(3)) {
            val parts = line.split('\t')
            require(parts.size == labels.size + 1 && parts[0].matches(Regex("[wbc]:[a-z_]+")))
            val values = parts.drop(1).map(String::toInt)
            require(values.all { it >= 0 } && values.any { it > 0 })
            require(counts.put(parts[0], values) == null)
        }
        require(counts.isNotEmpty())
        return NaiveBayesParameters(topics, documents, counts, alpha = 0.1)
    }
}
