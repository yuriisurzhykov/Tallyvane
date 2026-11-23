package tallyvane.knowledge.infra

/** The application cannot start, for a reason the person running it can fix; the message says how. */
class StartupFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
