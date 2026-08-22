package tallyvane.jobs.infrastructure

class PostgresJobs {
    fun sql(table: String): String = "SELECT * FROM " + table
}
