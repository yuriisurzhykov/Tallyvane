package konsist.fixtures.openapicoversroutes

// An endpoint served under a path docs/openapi.yaml has never heard of: a wire contract exists, and
// no client was generated for it. §11.7 makes that file the source of truth, so this is the file
// being wrong rather than the spec being incomplete.
class NowhereRoutes {
    val basePath: Any = BasePath("/nowhere")

    private class BasePath(val value: String)

    private fun BasePath(value: String): BasePath = BasePath(value)
}
