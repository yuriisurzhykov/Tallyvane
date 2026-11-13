package tallyvane.platform.llm

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import tallyvane.platform.kernel.Secret
import java.net.http.HttpClient
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

/**
 * The client of each provider, built once and kept until the provider's settings change.
 */
internal class ClientSpeakers : Speakers {
    private val built = ConcurrentHashMap<String, Speaker>()
    private val http: HttpClient = HttpClient.newHttpClient()

    override fun of(id: Uuid, kind: ProviderKind, baseUrl: String?, key: Secret): Speaker =
        built.computeIfAbsent("$id|$kind|$baseUrl|${key.hashCode()}") {
            when (kind) {
                ProviderKind.Anthropic -> AnthropicSpeaker(
                    AnthropicOkHttpClient.builder().apiKey(key.revealed())
                        .apply { baseUrl?.let { url -> baseUrl(url) } }
                        .build(),
                )
                ProviderKind.OpenAiCompatible ->
                    OpenAiSpeaker(
                        http,
                        checkNotNull(baseUrl) {
                            "An OpenAI-compatible provider needs an address."
                        },
                        key,
                    )
            }
        }

    override fun toString(): String = "ClientSpeakers(${built.size})"
}
