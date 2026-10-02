import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { Dialog } from "./Dialog";

describe("Dialog", () => {
    it("renders its title and description in a dialog role when open", () => {
        render(
            <Dialog.Root open>
                <Dialog.Popup>
                    <Dialog.Title>Signed out</Dialog.Title>
                    <Dialog.Description>Sign in again to continue</Dialog.Description>
                </Dialog.Popup>
            </Dialog.Root>,
        );
        const dialog = screen.getByRole("dialog", { name: "Signed out" });
        expect(dialog).toHaveAccessibleDescription("Sign in again to continue");
    });

    it("renders nothing when closed", () => {
        render(
            <Dialog.Root open={false}>
                <Dialog.Popup>
                    <Dialog.Title>Signed out</Dialog.Title>
                </Dialog.Popup>
            </Dialog.Root>,
        );
        expect(screen.queryByRole("dialog")).toBeNull();
    });
});
