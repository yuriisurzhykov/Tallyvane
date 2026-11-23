/** Addresses of common providers that speak OpenAI's chat completions, to fill the form in one tap. */
export const PRESETS: readonly { readonly name: string; readonly kind: "anthropic" | "openai_compatible"; readonly baseUrl: string }[] = [
    { name: "Anthropic", kind: "anthropic", baseUrl: "" },
    { name: "OpenAI", kind: "openai_compatible", baseUrl: "https://api.openai.com/v1" },
    { name: "OpenRouter", kind: "openai_compatible", baseUrl: "https://openrouter.ai/api/v1" },
    { name: "Gemini", kind: "openai_compatible", baseUrl: "https://generativelanguage.googleapis.com/v1beta/openai" },
    { name: "DeepSeek", kind: "openai_compatible", baseUrl: "https://api.deepseek.com/v1" },
    { name: "Ollama", kind: "openai_compatible", baseUrl: "http://host.docker.internal:11434/v1" },
];
