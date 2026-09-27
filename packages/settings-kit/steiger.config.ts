import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

export default defineConfig([
    ...fsd.configs.recommended,
    { ignores: ["**/*.md"] },
    {
        files: ["./src/**"],
        rules: {
            "fsd/insignificant-slice": "off",
            "fsd/no-public-api-sidestep": "off",
        },
    },
]);
