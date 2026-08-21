import type { Metadata } from "next";

export const metadata: Metadata = {
    title: "Design system — Tallyvane",
    robots: { index: false, follow: false },
};

export { DesignSystemStorybook as default } from "./token-catalog-sections";
