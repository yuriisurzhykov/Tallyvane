import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Fieldset } from "./Fieldset";

describe("Fieldset", () => {
    it("renders the legend text", () => {
        render(
            <Fieldset legend="Notification preferences">
                <input aria-label="Email alerts" type="checkbox" />
            </Fieldset>,
        );

        expect(screen.getByText("Notification preferences")).toBeInTheDocument();
    });

    it("labels the group with the legend as its accessible name", () => {
        render(
            <Fieldset legend="Notification preferences">
                <input aria-label="Email alerts" type="checkbox" />
            </Fieldset>,
        );

        expect(screen.getByRole("group", { name: "Notification preferences" })).toBeInTheDocument();
    });

    it("renders its children inside the group", () => {
        render(
            <Fieldset legend="Notification preferences">
                <input aria-label="Email alerts" type="checkbox" />
            </Fieldset>,
        );

        expect(screen.getByRole("checkbox", { name: "Email alerts" })).toBeInTheDocument();
    });
});
