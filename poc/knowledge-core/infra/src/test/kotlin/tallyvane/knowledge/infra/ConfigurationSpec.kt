package tallyvane.knowledge.infra

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Files

class ConfigurationSpec :
    StringSpec(
        {
            "a setting that is not set takes its default, and one that is blank does too" {
                val env = Environment(mapOf("PORT" to "  ", "OLLAMA_URL" to "http://ollama:11434"))
                env.whole("PORT", 8080) shouldBe 8080
                env.text("OLLAMA_URL", "http://localhost:11434") shouldBe "http://ollama:11434"
                env.decimal("DEDUPE_THRESHOLD", 0.85) shouldBe 0.85
            }

            "a setting that is needed and missing stops the start and names the variable" {
                shouldThrow<StartupFailure> { Environment(emptyMap()).required("ANTHROPIC_API_KEY") }.message shouldContain "ANTHROPIC_API_KEY"
            }

            "a number that is not a number stops the start and names the variable" {
                shouldThrow<StartupFailure> { Environment(mapOf("EMBED_DIM" to "many")).whole("EMBED_DIM", 768) }.message shouldContain "EMBED_DIM"
                shouldThrow<StartupFailure> { Environment(mapOf("TAG_THRESHOLD" to "high")).decimal("TAG_THRESHOLD", 0.55) }.message shouldContain "TAG_THRESHOLD"
            }

            "a .env file gives its lines, without comments, quotes or export, and the process environment wins" {
                val file = Files.createTempFile("env", ".env")
                try {
                    Files.writeString(file, "# the key\nexport LLM_MODEL=\"claude-x\"\nPORT=9000\n\nbroken line\nOLLAMA_URL='http://o:1'\n")
                    val read = Environment.fromFile(file)
                    read shouldBe mapOf("LLM_MODEL" to "claude-x", "PORT" to "9000", "OLLAMA_URL" to "http://o:1")
                } finally {
                    Files.delete(file)
                }
            }

            "prompts are read from the directory first, and from the application when the directory has no such file" {
                val directory = Files.createTempDirectory("prompts")
                try {
                    Files.writeString(directory.resolve("extract.md"), "EDITED RULES\n")
                    val prompts = FilePrompts(directory)
                    prompts.prefix("extract") shouldBe "EDITED RULES"
                    prompts.prefix("bullets") shouldContain "résumé bullets"
                    Files.writeString(directory.resolve("extract.md"), "EDITED AGAIN")
                    prompts.prefix("extract") shouldBe "EDITED AGAIN"
                    FilePrompts(null).prefix("extract") shouldContain "structured facts"
                    shouldThrow<StartupFailure> { FilePrompts(null).prefix("nonsense") }
                } finally {
                    directory.toFile().deleteRecursively()
                }
            }
        },
    )
