package tallyvane.knowledge.infra

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCio
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import tallyvane.knowledge.application.KnowledgeCore
import tallyvane.knowledge.application.Settings
import tallyvane.knowledge.infra.llm.AnthropicClient
import tallyvane.knowledge.infra.llm.OllamaEmbeddings
import tallyvane.knowledge.infra.llm.RecordingLlmClient
import tallyvane.knowledge.infra.postgres.Postgres
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.system.exitProcess

private const val MODEL_TIMEOUT_MILLIS = 120_000L
private val SOURCE_PROMPTS = Path.of("infra/src/main/resources/prompts")

/** The composition root: reads the settings, builds each part once, hands them to [KnowledgeCore], and serves. */
fun main() {
    val log = LoggerFactory.getLogger("knowledge-core")
    try {
        serve(Environment.ofProcess())
    } catch (e: StartupFailure) {
        log.error(e.message)
        exitProcess(1)
    }
}

private fun serve(env: Environment) {
    val embeddingDimensions = env.whole("EMBED_DIM", 768)
    val http =
        HttpClient(ClientCio) {
            install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) { requestTimeoutMillis = MODEL_TIMEOUT_MILLIS }
        }
    val postgres =
        Postgres.open(
            env.text("DATABASE_URL", "jdbc:postgresql://localhost:5432/knowledge"),
            env.text("DATABASE_USER", "knowledge"),
            env.text("DATABASE_PASSWORD", "knowledge"),
            embeddingDimensions,
        )
    val embeddings = OllamaEmbeddings(http, env.text("OLLAMA_URL", "http://localhost:11434"), env.text("EMBED_MODEL", "nomic-embed-text"))
    val dictionary = runBlocking { Seeding.bundled(postgres, embeddings, embeddingDimensions).run() }

    val llm =
        RecordingLlmClient(
            AnthropicClient(
                http,
                env.required("ANTHROPIC_API_KEY"),
                env.required("LLM_MODEL"),
                env.text("ANTHROPIC_URL", "https://api.anthropic.com"),
            ),
        )
    val store = postgres.knowledge()
    val core =
        KnowledgeCore(
            graph = store,
            cursors = store,
            log = store,
            skills = store,
            vacancies = store,
            llm = llm,
            embeddings = embeddings,
            prompts = FilePrompts(promptsDirectory(env)),
            dictionary = dictionary,
            diagnostics = LoggedDiagnostics(),
            settings = Settings(env.decimal("DEDUPE_THRESHOLD", 0.85), env.decimal("TAG_THRESHOLD", 0.55)),
            today = { LocalDate.now() },
        )

    val port = env.whole("PORT", 8080)
    LoggerFactory.getLogger("knowledge-core").info("Serving on http://localhost:$port")
    embeddedServer(CIO, port = port) { Api(core, llm::recent).install(this) }.start(wait = true)
}

/** `PROMPTS_DIR` if set, else the prompts in the source tree when run from the root of the build, else only the built-in ones. */
private fun promptsDirectory(env: Environment): Path? =
    env.text("PROMPTS_DIR", "").takeIf { it.isNotEmpty() }?.let(Path::of) ?: SOURCE_PROMPTS.takeIf { Files.isDirectory(it) }
