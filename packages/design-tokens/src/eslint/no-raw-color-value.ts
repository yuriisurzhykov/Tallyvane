/** DS001, inline `style={{}}` side. */
import type { Rule } from "eslint";
import { isStyleAttribute, propertyKeyName } from "./ast-helpers.ts";

const COLOR_PROPERTY = /^(color|background|backgroundColor|background(Color)?Image|border(Color)?|borderTop(Color)?|borderBottom(Color)?|borderLeft(Color)?|borderRight(Color)?|fill|stroke|outline(Color)?|boxShadow|textDecorationColor|caretColor|accentColor)$/;
const RAW_COLOR_VALUE = /^(#[0-9a-fA-F]{3,8}|rgba?\(|hsla?\(|oklch\(|oklab\(|lab\(|lch\()/;

/** Reports every raw color literal a single style property's value resolves to — a plain string, or a template literal's static segments. */
function reportRawColorValues(context: Rule.RuleContext, keyName: string, propertyValue: any): void {
    if (propertyValue.type === "Literal" && typeof propertyValue.value === "string") {
        const raw = propertyValue.value.trim();
        if (RAW_COLOR_VALUE.test(raw)) {
            context.report({ node: propertyValue, messageId: "rawColorInInlineStyle", data: { property: keyName, value: raw } });
        }
        return;
    }
    if (propertyValue.type === "TemplateLiteral") {
        for (const quasi of propertyValue.quasis) {
            const raw = quasi.value.raw.trim();
            if (RAW_COLOR_VALUE.test(raw)) {
                context.report({ node: propertyValue, messageId: "rawColorInInlineStyle", data: { property: keyName, value: raw } });
                break;
            }
        }
    }
}

/** Filters one `style={{}}` property down to the color-bearing ones before checking its value. */
function checkStyleProperty(context: Rule.RuleContext, property: any): void {
    if (property.type !== "Property") return;
    const keyName = propertyKeyName(property.key);
    if (!keyName || !COLOR_PROPERTY.test(keyName)) return;
    reportRawColorValues(context, keyName, property.value);
}

const rule: Rule.RuleModule = {
    meta: {
        type: "problem",
        docs: {
            description: "Disallow a raw color literal inside an inline style={{}} — concrete colors exist only in tokens/color.ts; use a CSS variable backed by a design token instead.",
        },
        schema: [],
        messages: {
            rawColorInInlineStyle:
                'Inline style property "{{property}}" has a raw color literal ("{{value}}"). Reference a CSS variable backed by a design token instead.',
        },
    },
    create(context) {
        return {
            JSXAttribute(node: any) {
                if (!isStyleAttribute(node)) return;
                const value = (node).value;
                if (value?.type !== "JSXExpressionContainer") return;
                const expression = value.expression;
                if (expression?.type !== "ObjectExpression") return;

                for (const property of expression.properties) {
                    checkStyleProperty(context, property);
                }
            },
        };
    },
};

export default rule;
