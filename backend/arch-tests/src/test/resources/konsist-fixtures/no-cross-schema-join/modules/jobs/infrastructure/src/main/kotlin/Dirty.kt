package tallyvane.jobs.infrastructure

class PostgresJobs {
    fun query() = innerJoin("contacts.person")
}

fun innerJoin(table: String): String = table
