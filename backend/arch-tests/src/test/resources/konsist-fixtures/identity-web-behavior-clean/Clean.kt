package tallyvane.identity.web.clean

data class Request(val value: String)

interface Boundary {
    fun execute()

    class Runner : Boundary {
        override fun execute() = Unit
    }
}
