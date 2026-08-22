package tallyvane.jobs.application.port

interface Jobs {
    fun save()

    class Fake : Jobs {
        override fun save() {}
    }
}

class PostgresJobs : Jobs {
    override fun save() {}
}
