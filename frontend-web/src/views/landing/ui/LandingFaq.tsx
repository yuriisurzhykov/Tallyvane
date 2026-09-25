"use client";

import { Accordion } from "frontend-shared/ui/accordion";
import { Text } from "frontend-shared/ui/text";
import { useStrings } from "@/app/i18n";
import { nativeRender } from "./native";

const FAQ_ITEMS = [
    { value: "what", titleKey: "faqWhatTitle", answerKey: "faqWhatAnswer" },
    { value: "spreadsheet", titleKey: "faqSpreadsheetTitle", answerKey: "faqSpreadsheetAnswer" },
    { value: "not-do", titleKey: "faqNotDoTitle", answerKey: "faqNotDoAnswer" },
    { value: "who-for", titleKey: "faqWhoForTitle", answerKey: "faqWhoForAnswer" },
] as const;

/** Four-question FAQ. Each accordion label is an `h3` under the section heading. */
export function LandingFaq() {
    const t = useStrings("landing");

    return (
        <Accordion.Root defaultValue={["what"]}>
            {FAQ_ITEMS.map((item) => (
                <Accordion.Item key={item.value} value={item.value}>
                    <Accordion.Header render={nativeRender("h3")}>
                        <Accordion.Trigger>{t(item.titleKey)}</Accordion.Trigger>
                    </Accordion.Header>
                    <Accordion.Panel>
                        <Text variant="body" color="secondary" className="px-stack pb-stack">{t(item.answerKey)}</Text>
                    </Accordion.Panel>
                </Accordion.Item>
            ))}
        </Accordion.Root>
    );
}
