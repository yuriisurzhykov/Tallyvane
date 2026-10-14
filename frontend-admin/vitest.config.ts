import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

export default defineConfig({
    resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
    test: {
        // The logic worth testing here is plain classes. A component is looked at in a browser, not in jsdom.
        environment: "node",
        include: ["src/**/*.test.ts"],
    },
});
