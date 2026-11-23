package tallyvane.knowledge.domain

import kotlin.math.sqrt

/** The position of a piece of text in meaning space. Two are compared by the angle between them. */
class Embedding(private val values: FloatArray) {
    init {
        require(values.isNotEmpty()) { "An embedding has at least one dimension." }
    }

    /** Cosine similarity, from -1 to 1. A vector of zeros is similar to nothing. */
    fun similarityTo(other: Embedding): Double {
        require(values.size == other.values.size) { "Embeddings of ${values.size} and ${other.values.size} dimensions." }
        var dot = 0.0
        var mine = 0.0
        var theirs = 0.0
        for (i in values.indices) {
            dot += values[i] * other.values[i]
            mine += values[i] * values[i]
            theirs += other.values[i] * other.values[i]
        }
        return if (mine == 0.0 || theirs == 0.0) 0.0 else dot / (sqrt(mine) * sqrt(theirs))
    }

    fun writeTo(record: Record) = record.embedding(values.copyOf())

    interface Record {
        fun embedding(values: FloatArray)
    }
}
