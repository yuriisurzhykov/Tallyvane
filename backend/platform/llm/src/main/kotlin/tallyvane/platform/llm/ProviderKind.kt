package tallyvane.platform.llm

/**
 * How a provider is spoken to: Anthropic's Messages API, or any API that speaks OpenAI's chat completions
 * (OpenAI, OpenRouter, Gemini, DeepSeek, Groq, a local Ollama…).
 */
public enum class ProviderKind { Anthropic, OpenAiCompatible }
