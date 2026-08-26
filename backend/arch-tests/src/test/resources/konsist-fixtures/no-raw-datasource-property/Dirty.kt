package konsist.fixtures.norawdatasourceproperty

// Passing the bound directly, as the production config did until it was measured: HikariCP
// stores the Int and pgjdbc never reads it, so the pool has no socket bound at all.
class Pool(private val configuration: Any) {
    fun bound() {
        configuration.addDataSourceProperty("socketTimeout", 30)
    }

    private fun Any.addDataSourceProperty(name: String, value: Any) {
        check(name.isNotEmpty() && value != Unit)
    }
}
