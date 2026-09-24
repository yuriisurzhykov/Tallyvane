import { defineConfig } from "@playwright/test";

const port = 3300;
const baseURL = `http://localhost:${ String(port) }`;

export default defineConfig({
    testDir: "./tests/e2e",
    testMatch: "auth-app.spec.ts",
    workers: 1,
    reporter: "list",
    use: { baseURL, browserName: "chromium", viewport: { width: 1440, height: 900 } },
    webServer: {
        command: "pnpm --dir ../frontend-app dev",
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
    },
});
