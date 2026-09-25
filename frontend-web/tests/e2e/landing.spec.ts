import { expect, test } from "@playwright/test";

test("landing page explains connected career memory and sends visitors to registration", async ({ page }) => {
    await page.goto("/");

    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Your experience, connected.");
    const registrationAction = page
        .getByRole("region", { name: "Your experience, connected." })
        .getByRole("button", { name: "Get started" });
    await expect(registrationAction).toHaveAttribute(
        "href",
        "https://app.surzhykov.icu/register",
    );
    await expect(registrationAction).toHaveJSProperty("tagName", "A");
    await expect(page.getByText(
        "Tell Tallyvane what you have done. It keeps the context, connects the details, and helps you turn your experience into the next move.",
    )).toBeVisible();
    await expect(page.getByText("Onboarding redesign")).toBeVisible();
    await expect(page.getByText("Product discovery")).toBeVisible();
    await expect(page.getByText("Higher activation")).toBeVisible();
    await expect(page.getByText("Turn user insight into product direction")).toBeVisible();
    await expect(page.getByText("Bring this project into your résumé")).toBeVisible();
    await expect(page.getByText("Estimated take-home pay")).toBeVisible();
    await expect(page.getByRole("heading", { name: "One experience. Connected evidence." })).toBeVisible();
});
