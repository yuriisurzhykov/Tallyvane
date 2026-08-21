import { Linter } from "eslint";
import { describe, expect, it } from "vitest";
import rule from "./no-raw-icon-size";
import { onlyMessage } from "./test-helpers";

const linter = new Linter();

function lint(code: string) {
    return linter.verify(code, {
        languageOptions: { ecmaVersion: 2022, sourceType: "module", parserOptions: { ecmaFeatures: { jsx: true } } },
        plugins: { local: { rules: { "no-raw-icon-size": rule } } },
        rules: { "local/no-raw-icon-size": "error" },
    });
}

describe("no-raw-icon-size", () => {
    it("flags lucide-react size={16} and size={ICON_SIZE} when ICON_SIZE is a number constant", () => {
        expect(lint('import { Check } from "lucide-react"; const x = <Check size={16} />;')).toHaveLength(1);
        expect(lint('import { Check } from "lucide-react"; const ICON_SIZE = 14; const x = <Check size={ICON_SIZE} />;')).toHaveLength(1);
        expect(onlyMessage(lint('import { X } from "lucide-react"; const x = <X size={16} />;'))).toContain("hardcoded dimension");
    });

    it("flags a namespace import Lucide.Check size={16}", () => {
        expect(lint('import * as Lucide from "lucide-react"; const x = <Lucide.Check size={16} />;')).toHaveLength(1);
    });

    it("does not flag Button/Input size variant props", () => {
        expect(lint('const x = <Button size="sm" />;')).toHaveLength(0);
        expect(lint('const x = <Input size="md" />;')).toHaveLength(0);
    });

    it("does not flag a lucide icon without a size prop", () => {
        expect(lint('import { Check } from "lucide-react"; const x = <Check className="h-(--control-icon) w-(--control-icon)" />;')).toHaveLength(0);
    });

    it("does not flag size passed through as a parameter", () => {
        expect(lint('import { Check } from "lucide-react"; function Icon({ size }) { return <Check size={size} />; }')).toHaveLength(0);
    });

    it("silences only with a complete @architecture-exception for this rule", () => {
        expect(lint(`
            import { Check } from "lucide-react";
            function Comp() {
                // @architecture-exception rule=no-raw-icon-size adr=ADR-042
                //   reason=test
                return <Check size={16} />;
            }
        `)).toHaveLength(0);
    });
});
