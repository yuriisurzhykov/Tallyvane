package tallyvane.knowledge.infra.postgres

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

// What Exposed does not know about Postgres: pgvector's `vector`, the `jsonb` that the PoC keeps as text, and
// the distance operator. Each is the smallest thing that lets the adapter write and read them.

/** A pgvector `vector`, written and read as the `[1,2,3]` text pgvector speaks. */
internal class VectorColumnType : ColumnType<FloatArray>() {
    override fun sqlType(): String = "vector"

    override fun valueFromDB(value: Any): FloatArray =
        when (value) {
            is FloatArray -> value
            is PGobject -> parse(checkNotNull(value.value) { "A vector with no value." })
            is String -> parse(value)
            else -> error("Cannot read a vector from ${value::class}.")
        }

    override fun notNullValueToDB(value: FloatArray): Any = PGobject().apply {
        type = "vector"
        this.value = text(value)
    }

    override fun nonNullValueToString(value: FloatArray): String = "'${text(value)}'"

    companion object {
        fun text(values: FloatArray): String = values.joinToString(prefix = "[", postfix = "]", separator = ",")

        private fun parse(text: String): FloatArray =
            text.trim().removePrefix("[").removeSuffix("]").split(',').map { it.trim().toFloat() }.toFloatArray()
    }
}

/** A `jsonb` column whose value the adapter holds as the JSON text. */
internal class JsonbColumnType : ColumnType<String>() {
    override fun sqlType(): String = "jsonb"

    override fun valueFromDB(value: Any): String =
        when (value) {
            is PGobject -> checkNotNull(value.value) { "A jsonb with no value." }
            is String -> value
            else -> error("Cannot read jsonb from ${value::class}.")
        }

    override fun notNullValueToDB(value: String): Any = PGobject().apply {
        type = "jsonb"
        this.value = value
    }

    override fun nonNullValueToString(value: String): String = "'${value.replace("'", "''")}'"
}

internal fun Table.vector(name: String): Column<FloatArray> = registerColumn(name, VectorColumnType())

internal fun Table.jsonb(name: String): Column<String> = registerColumn(name, JsonbColumnType())

internal fun Table.textArray(name: String): Column<List<String>> = array<String>(name)

/** pgvector's cosine distance, `<=>`: 0 for the same direction, 1 for a right angle. Similarity is one minus it. */
internal class CosineDistance(private val column: Expression<*>, private val vector: FloatArray) :
    ExpressionWithColumnType<Double>() {
    override val columnType = DoubleColumnType()

    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder {
            append(column)
            append(" <=> ")
            registerArgument(VectorColumnType(), vector)
        }
    }
}

/** What `1 - distance` means as a similarity: pgvector answers NaN for a vector of zeros, which is similar to nothing. */
internal fun similarityOf(distance: Double): Double = if (distance.isNaN()) 0.0 else 1.0 - distance
