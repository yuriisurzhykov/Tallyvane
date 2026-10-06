import { describe, expect, it } from "vitest";
import { TypedCode } from "./TypedCode";

describe("TypedCode", () => {
    it("keeps the digits of a code from the app, however they were grouped", () => {
        expect(new TypedCode("123 456").forKind("totp")).toBe("123456");
        expect(new TypedCode("123-456").forKind("totp")).toBe("123456");
    });

    it("keeps a recovery code in capitals without dashes or spaces", () => {
        expect(new TypedCode("abcde-fghjk").forKind("recovery_code")).toBe("ABCDEFGHJK");
        expect(new TypedCode(" ABCDE FGHJK ").forKind("recovery_code")).toBe("ABCDEFGHJK");
    });

    it("is complete at six digits for the app and ten characters for a recovery code, and not before", () => {
        expect(new TypedCode("12345").isComplete("totp")).toBe(false);
        expect(new TypedCode("123456").isComplete("totp")).toBe(true);
        expect(new TypedCode("1234567").isComplete("totp")).toBe(false);
        expect(new TypedCode("abcde-fghj").isComplete("recovery_code")).toBe(false);
        expect(new TypedCode("abcde-fghjk").isComplete("recovery_code")).toBe(true);
    });

    it("does not take letters for the app's digits", () => {
        expect(new TypedCode("12a456").isComplete("totp")).toBe(false);
    });
});
