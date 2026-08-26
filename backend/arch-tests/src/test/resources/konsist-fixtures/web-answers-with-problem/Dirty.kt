package tallyvane.jobs.web

// Answering with a bare status and a bare string: no `type` URI a client can branch on, no
// problem+json content type, no trace id, no log line. It compiles and it answers, which is why
// only a gate catches it.
class JobRoutes {
    fun refuse(): Pair<Any, String> = HttpStatusCode.BadRequest to "oops"

    private object HttpStatusCode {
        val BadRequest: Any = 400
    }
}
