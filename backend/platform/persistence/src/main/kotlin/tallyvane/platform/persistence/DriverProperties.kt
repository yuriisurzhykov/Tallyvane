package tallyvane.platform.persistence

import com.zaxxer.hikari.HikariConfig

/**
 * The only way this module hands a setting to the JDBC driver.
 *
 * ### Why this exists rather than calling `addDataSourceProperty` directly
 *
 * `addDataSourceProperty("socketTimeout", 30)` compiles, is accepted, is stored — and does
 * nothing. HikariCP keeps these in a [java.util.Properties] and, since 6.3.1, copies them with
 * `putAll` instead of `setProperty(key.toString(), value.toString())`, so an `Int` stays an
 * `Integer`. pgjdbc reads its settings with `Properties.getProperty`, which returns `null` for any
 * value that is not a `String`. The entry is present and invisible, with no warning from either
 * library.
 *
 * That is not a hypothetical: this module shipped `socketTimeout` and `connectTimeout` as `Int`
 * constants, under a comment calling them the bounds that actually stop a hung connection, and
 * neither was in effect. `playground/timeout-bounds/README.md` has the measurement and the
 * upstream commit that changed the behaviour — the configuration was correct when written and an
 * upgrade turned it into a no-op without touching the code.
 *
 * So the conversion happens in one place instead of being remembered at each call site, and
 * `no-raw-datasource-property` keeps it that way.
 */
internal class DriverProperties(private val onto: HikariConfig) {
    /**
     * @param value converted with `toString`, because that is the only form pgjdbc can read.
     */
    fun set(name: String, value: Any) {
        onto.addDataSourceProperty(name, value.toString())
    }
}
