import type { NextConfig } from "next";

/**
 * Identical to `frontend-web/next.config.ts` — same workspace packages, same
 * reason: pnpm links each as a real directory of raw TypeScript source.
 */
const config: NextConfig = {
    transpilePackages: ["design-token-engine", "frontend-shared", "content-kit"],
};

export default config;
