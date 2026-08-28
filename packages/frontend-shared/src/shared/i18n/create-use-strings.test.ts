import { describe, expect, it } from "vitest";
import { createUseStrings } from "./index";

const dictionary = {
    common: {
        productName: "Tallyvane",
        greet: "Hello {name}",
        count: "Hello {name}, you have {count} left",
    },
    other: {
        unused: "not read",
    },
};

const useStrings = createUseStrings(dictionary);

describe("createUseStrings", () => {
    it("returns the string for a typed namespace.key", () => {
        const t = useStrings("common");
        expect(t("productName")).toBe("Tallyvane");
    });

    it("substitutes {name} placeholders from the vars map", () => {
        const t = useStrings("common");
        expect(t("greet", { name: "Ada" })).toBe("Hello Ada");
    });

    it("substitutes every placeholder in one pass, including numeric values", () => {
        const t = useStrings("common");
        expect(t("count", { name: "Ada", count: 3 })).toBe("Hello Ada, you have 3 left");
    });

    it("leaves a placeholder untouched when that name is missing from vars", () => {
        const t = useStrings("common");
        expect(t("greet", { other: "ignored" })).toBe("Hello {name}");
    });

    it("returns the raw template when vars is omitted", () => {
        const t = useStrings("common");
        expect(t("greet")).toBe("Hello {name}");
    });

    it("rejects an unknown namespace at compile time", () => {
        // @ts-expect-error — only keys of the dictionary are valid namespaces
        const invalid = () => useStrings("missing");
        expect(invalid).toBeDefined();
    });

    it("rejects an unknown key at compile time", () => {
        const t = useStrings("common");
        // @ts-expect-error — only keys of the chosen namespace are valid
        const invalid = () => t("missing");
        expect(invalid).toBeDefined();
    });

    it("throws if a missing key is forced past the type checker", () => {
        const t = useStrings("common");
        expect(() => t("missing" as "productName")).toThrow(/Unknown string key/);
    });
});
