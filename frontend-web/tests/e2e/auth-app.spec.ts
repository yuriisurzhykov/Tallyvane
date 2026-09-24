import { expect, test } from "@playwright/test";

test.describe("the application authentication pages", () => {
    test.beforeEach(async ({ page }) => {
        await page.route("**/api/v1/auth/providers", route => route.fulfill({ json: { google: false } }));
        await page.goto("/register");
    });

    test("starts with Google and a clear, keyboard-operable email disclosure", async ({ page }) => {
        await expect(page.getByRole("heading", { name: "Your next chapter starts here." })).toBeVisible();
        await expect(page.getByRole("button", { name: /Continue with Google/ })).toBeVisible();
        await expect(page.getByRole("region", { name: "Notifications" })).toHaveCount(1);

        const disclosure = page.locator("summary");
        await expect(disclosure).toBeVisible();
        await expect(page.getByLabel("Email address")).toBeHidden();
        await page.getByRole("button", { name: "Change color theme" }).focus();
        await page.keyboard.press("Tab");
        await expect(disclosure).toBeFocused();
        await page.keyboard.press("Enter");

        const email = page.getByLabel("Email address");
        const password = page.getByLabel("Password");
        await expect(email).toBeVisible();
        await expect(password).toBeVisible();
        await expect(page.getByRole("button", { name: "Create account" })).toBeVisible();
        expect(await email.evaluate(element => getComputedStyle(element).borderTopWidth)).not.toBe("0px");
        expect(await page.getByRole("button", { name: "Create account" }).evaluate(element => getComputedStyle(element).backgroundColor)).not.toBe("rgba(0, 0, 0, 0)");

        await page.getByRole("button", { name: "Change color theme" }).click();
        expect(await email.evaluate(element => getComputedStyle(element).borderTopWidth)).not.toBe("0px");

        await page.goto("/auth-preview");
        await page.getByLabel("Preview screen").selectOption("mfa");
        await expect(page.getByLabel("Authenticator code")).toHaveAttribute("autocomplete", "one-time-code");
    });

    test("keeps field errors beside their field and announces form-wide failures in a toast", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/register/password", route => route.fulfill({
            status: 400,
            contentType: "application/problem+json",
            json: { detail: "Check the email address.", errors: { email: "Enter a valid address." } },
        }));

        await page.getByText("Continue with email", { exact: true }).click();
        await page.getByLabel("Name (optional)").fill("Taylor");
        await page.getByLabel("Email address").fill("taylor@example.test");
        await page.getByLabel("Password").fill("a long memorable passphrase");
        await page.getByRole("button", { name: "Create account" }).click();

        const email = page.getByLabel("Email address");
        await expect(email).toHaveAttribute("aria-invalid", "true");
        await expect(email).toBeFocused();
        const descriptionIds = await email.getAttribute("aria-describedby");
        expect(descriptionIds).not.toBeNull();
        const errorId = descriptionIds!.split(/\s+/)[0]!;
        await expect(page.locator(`[id="${errorId}"]`)).toHaveText("Enter a valid address.");
        await expect(page.getByText("Authentication failed")).toHaveCount(0);

        await page.unroute("**/api/v1/auth/register/password");
        await page.route("**/api/v1/auth/register/password", route => route.fulfill({
            status: 503,
            contentType: "application/problem+json",
            json: { detail: "The email service is temporarily unavailable." },
        }));
        await page.getByRole("button", { name: "Create account" }).click();
        const toast = page.getByText("Authentication failed");
        await expect(toast).toBeVisible();
        const bounds = await toast.evaluate(element => element.getBoundingClientRect().toJSON());
        const viewport = page.viewportSize();
        expect(viewport).not.toBeNull();
        expect(bounds.x).toBeGreaterThan(viewport!.width / 2);
        expect(bounds.y).toBeGreaterThan(viewport!.height / 2);
    });

    test("completes email-code sign-in and continues to the server-selected MFA step", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/login/email/code", route => route.fulfill({ status: 202, json: { challengeId: "challenge-123" } }));
        await page.route("**/api/v1/auth/login/email/verify", route => route.fulfill({ json: {
            status: "requires_second_factor", pendingId: "pending-123", availableMethods: ["TOTP", "BACKUP_CODE"],
        } }));
        await page.goto("/login");
        await page.getByRole("link", { name: "Sign in with an email code" }).click();
        await expect(page.getByRole("heading", { name: "Check your inbox." })).toBeVisible();
        await page.getByLabel("Email address").fill("taylor@example.test");
        await page.getByRole("button", { name: "Email me a sign-in code" }).click();
        await expect(page.getByText("Sign-in code sent")).toBeVisible();
        await expect(page.getByLabel("Verification code")).toBeVisible();
        await page.getByLabel("Verification code").fill("123456");
        await page.getByRole("button", { name: "Verify and continue" }).click();
        await expect(page).toHaveURL(/\/mfa$/);
        await expect(page.getByLabel("Verification method")).toHaveValue("TOTP");
    });

    test("recovers a password with an email code and confirms completion", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/password/forgot", route => route.fulfill({ status: 202, json: { challengeId: "reset-123" } }));
        await page.route("**/api/v1/auth/password/reset", route => route.fulfill({ json: { status: "completed" } }));
        await page.goto("/forgot-password");
        await page.getByLabel("Email address").fill("taylor@example.test");
        await page.getByRole("button", { name: "Send reset code" }).click();
        await expect(page.getByLabel("Verification code")).toBeVisible();
        await page.getByLabel("Verification code").fill("123456");
        await page.getByLabel("New password").fill("a new memorable passphrase");
        await page.getByRole("button", { name: "Reset password" }).click();
        await expect(page.getByText("Password updated")).toBeVisible();
    });
});
