import { expect, test } from "@playwright/test";

test.describe("the application authentication pages", () => {
    test.beforeEach(async ({ page }) => {
        await page.route("**/api/v1/auth/providers", route => route.fulfill({ json: { google: false } }));
        await page.route("**/api/v1/auth/mfa/status", route => route.fulfill({ json: {
            enrolled: [], recentlyAuthenticated: false,
        } }));
    });

    test("starts with Google and a clear, keyboard-operable email disclosure", async ({ page }) => {
        await page.goto("/register");
        await expect(page.getByRole("heading", { name: "Your next chapter starts here." })).toBeVisible();
        await expect(page.getByRole("button", { name: /Continue with Google/ })).toBeVisible();
        await expect(page.getByRole("region", { name: "Notifications" })).toHaveCount(1);

        const disclosure = page.getByRole("button", { name: /Continue with email/ });
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
        await page.getByRole("combobox", { name: "Preview screen" }).click();
        await page.getByRole("option", { name: "One more check." }).click();
        await expect(page.getByLabel("Authenticator code")).toHaveAttribute("autocomplete", "one-time-code");
    });

    test("keeps field errors beside their field and announces form-wide failures in a toast", async ({ page }) => {
        await page.goto("/register");
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
        const errorId = descriptionIds?.split(/\s+/)[0];
        if (!errorId) throw new Error("The invalid email field should identify its error message.");
        await expect(page.locator(`[id="${errorId}"]`)).toHaveText("Enter a valid email address.");
        await expect(page.getByText("Authentication failed")).toHaveCount(0);

        await page.unroute("**/api/v1/auth/register/password");
        await page.route("**/api/v1/auth/register/password", route => route.fulfill({
            status: 503,
            contentType: "application/problem+json",
            json: { detail: "The email service is temporarily unavailable." },
        }));
        await page.getByLabel("Email address").fill("taylor+retry@example.test");
        await page.getByRole("button", { name: "Create account" }).click();
        const toast = page.getByText("Authentication failed");
        await expect(toast).toBeVisible();
        const bounds = await toast.evaluate(element => {
            const { x, y } = element.getBoundingClientRect();
            return { x, y };
        });
        const viewport = page.viewportSize();
        expect(viewport).not.toBeNull();
        if (!viewport) throw new Error("The browser viewport should be available in this test.");
        expect(bounds.x).toBeGreaterThan(viewport.width / 2);
        expect(bounds.y).toBeGreaterThan(viewport.height / 2);
    });

    test("completes email-code sign-in and continues to the server-selected MFA step", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/login/email/code", async route => {
            expect(route.request().postDataJSON()).toEqual({ email: "taylor@example.test" });
            await route.fulfill({ status: 202, json: { challenge_id: "challenge-123" } });
        });
        await page.route("**/api/v1/auth/login/email/verify", async route => {
            expect(route.request().postDataJSON()).toEqual({
                challenge_id: "challenge-123", email: "taylor@example.test", code: "123456", device: "Browser",
            });
            await route.fulfill({ json: {
                status: "requires_second_factor", pending_id: "pending-123", available_methods: ["TOTP", "BACKUP_CODE"],
            } });
        });
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
        await expect(page.getByRole("combobox", { name: "Verification method" })).toHaveText("Authenticator app");
    });

    test("keeps registration recoverable when SMTP fails and resends a code after cooldown", async ({ page }) => {
        test.setTimeout(90_000);
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/register/password", route => route.fulfill({ status: 201, json: { userId: "user-123", challengeId: null } }));
        await page.route("**/api/v1/auth/register/email/resend", route => route.fulfill({ status: 202, json: { challengeId: "challenge-456" } }));
        await page.route("**/api/v1/auth/register/email/verify", route => route.fulfill({ status: 204 }));

        await page.goto("/register");
        await page.getByText("Continue with email", { exact: true }).click();
        await page.getByLabel("Email address").fill("taylor@example.test");
        await page.getByLabel("Password").fill("a long memorable passphrase");
        await page.getByRole("button", { name: "Create account" }).click();
        await expect(page).toHaveURL(/\/otp\?purpose=registration$/);
        await expect(page.getByText("Email delivery is delayed.")).toBeVisible();
        const resend = page.getByRole("button", { name: /Resend code in/ });
        await expect(resend).toBeDisabled();

        const resendButton = page.getByRole("button", { name: "Resend verification code" });
        await expect(resendButton).toBeEnabled({ timeout: 65_000 });
        await resendButton.click();
        await expect(page.getByText("Check your email")).toBeVisible();
        await expect(page.getByRole("button", { name: "Verify email" })).toBeEnabled();
        await page.getByLabel("Verification code").fill("123456");
        await page.getByRole("button", { name: "Verify email" }).click();
        await expect(page.getByText("Email verified")).toBeVisible();
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

    test("changes an existing password after confirming the current password", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/account/password", async route => {
            expect(route.request().postDataJSON()).toEqual({
                current_password: "current memorable passphrase",
                new_password: "replacement memorable passphrase",
            });
            await route.fulfill({ status: 204 });
        });

        await page.goto("/account/security");
        await expect(page.getByRole("heading", { name: "Account security" })).toBeVisible();
        await page.getByLabel("Current password").fill("current memorable passphrase");
        await page.getByLabel("New password").fill("replacement memorable passphrase");
        await page.getByRole("button", { name: "Change password" }).click();
        await expect(page.getByRole("main").getByText("Your password has been changed.", { exact: true })).toBeVisible();
        await expect(page.getByRole("heading", { name: "Your password has been changed." })).toBeVisible();
    });

    test("replaces recovery codes only after asking for the current password and displays them once", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/mfa/backup-codes", async route => {
            expect(route.request().postDataJSON()).toEqual({ current_password: "current memorable passphrase" });
            await route.fulfill({ json: { codes: ["first-recovery-code", "second-recovery-code"] } });
        });

        await page.goto("/account/security");
        await page.getByLabel("Confirm your password").fill("current memorable passphrase");
        await page.getByRole("button", { name: "Generate new backup codes" }).click();
        await expect(page.getByText("first-recovery-code")).toBeVisible();
        await expect(page.getByText("second-recovery-code")).toBeVisible();
    });

    test("lists active sessions and confirms a meaningful session-revocation result", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/sessions", route => route.fulfill({ json: [{
            id: "session-123", device: "Chrome on MacBook", createdAt: "2026-01-01T00:00:00Z",
            lastUsedAt: "2026-01-02T00:00:00Z", revokedAt: null,
        }] }));
        await page.route("**/api/v1/auth/sessions/session-123", async route => {
            expect(route.request().method()).toBe("DELETE");
            await route.fulfill({ status: 204 });
        });

        await page.goto("/account/security");
        await expect(page.getByText("Chrome on MacBook")).toBeVisible();
        await page.getByRole("button", { name: "Sign out session" }).click();
        await expect(page.getByText("Chrome on MacBook")).not.toBeVisible();
        await expect(page.getByText("Session signed out")).toBeVisible();
    });

    test("shows an authenticator QR code and the manual setup key", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/mfa/enroll", route => route.fulfill({ json: {
            otpauthUri: "otpauth://totp/Tallyvane:taylor%40example.test?secret=JBSWY3DPEHPK3PXP&issuer=Tallyvane",
        } }));

        await page.goto("/mfa/enroll");
        await page.getByRole("button", { name: "Set up authenticator" }).click();
        await expect(page.getByRole("img", { name: "Authenticator setup QR code" })).toBeVisible();
        await expect(page.getByText("JBSWY3DPEHPK3PXP")).toBeVisible();
    });

    test("requests and verifies an email MFA code bound to its pending sign-in", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/mfa/email/request", async route => {
            expect(route.request().postDataJSON()).toEqual({ pending_id: "pending-123" });
            await route.fulfill({ status: 202, json: { challenge_id: "mfa-code-123" } });
        });
        await page.route("**/api/v1/auth/mfa/verify", async route => {
            expect(route.request().postDataJSON()).toEqual({
                pending_id: "pending-123", kind: "EMAIL_OTP", code: "123456", challenge_id: "mfa-code-123",
            });
            await route.fulfill({ json: { status: "issued" } });
        });

        await page.goto("/mfa?pending_id=pending-123&methods=EMAIL_OTP");
        await expect(page.getByRole("combobox", { name: "Verification method" })).toHaveText("Email code");
        await page.getByRole("button", { name: "Send email code" }).click();
        await expect(page.getByText("Check your inbox for an MFA code.")).toBeVisible();
        await page.getByLabel("Verification code").fill("123456");
        await page.getByRole("button", { name: "Verify and continue" }).click();
        await expect(page).toHaveURL(/\/today$/);
    });

    test("enrolls a required authenticator from the restricted sign-in challenge without issuing a session", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/login/password", route => route.fulfill({ json: {
            status: "requires_enrollment", pendingId: "pending-enroll-123", availableMethods: ["TOTP"], primaryMethod: "PASSWORD",
        } }));
        await page.route("**/api/v1/auth/mfa/required/enroll", async route => {
            expect(route.request().postDataJSON()).toEqual({ pending_id: "pending-enroll-123", kind: "TOTP" });
            await route.fulfill({ json: {
                otpauth_uri: "otpauth://totp/Tallyvane:taylor%40example.test?secret=JBSWY3DPEHPK3PXP&issuer=Tallyvane",
            } });
        });
        await page.route("**/api/v1/auth/mfa/required/confirm", async route => {
            expect(route.request().postDataJSON()).toEqual({ pending_id: "pending-enroll-123", kind: "TOTP", code: "123456" });
            await route.fulfill({ status: 204 });
        });

        await page.goto("/login");
        await page.getByLabel("Email address").fill("taylor@example.test");
        await page.getByLabel("Password").fill("a long memorable passphrase");
        await page.getByRole("button", { name: "Sign in" }).click();
        await expect(page).toHaveURL(/\/mfa\/enroll\?pending_id=pending-enroll-123$/);
        await page.getByRole("button", { name: "Set up authenticator" }).click();
        await expect(page.getByRole("img", { name: "Authenticator setup QR code" })).toBeVisible();
        await page.getByLabel("Code from your authenticator").fill("123456");
        await page.getByRole("button", { name: "Confirm authenticator" }).click();
        await expect(page.getByRole("status").filter({ hasText: "Authenticator enrolled. Sign in again to continue." })).toBeVisible();
        await expect(page.getByRole("link", { name: "Sign in" })).toBeVisible();
        await expect(page).not.toHaveURL(/\/today$/);
    });

    test("enrolls email MFA only after reauthentication and a purpose-bound code", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/mfa/email/enroll", async route => {
            expect(route.request().postDataJSON()).toEqual({ currentPassword: "current memorable passphrase" });
            await route.fulfill({ status: 202, json: { challengeId: "enrollment-123" } });
        });
        await page.route("**/api/v1/auth/mfa/email/confirm", async route => {
            expect(route.request().postDataJSON()).toEqual({ challenge_id: "enrollment-123", code: "123456" });
            await route.fulfill({ status: 204 });
        });

        await page.goto("/account/security");
        await page.getByLabel("Email MFA password").fill("current memorable passphrase");
        await page.getByRole("button", { name: "Enable email verification" }).click();
        await expect(page.getByLabel("Email MFA code")).toBeVisible();
        await page.getByLabel("Email MFA code").fill("123456");
        await page.getByRole("button", { name: "Confirm email verification" }).click();
        await expect(page.getByText("Email verification enabled.", { exact: true })).toBeVisible();
    });

    test("reauthenticates before disabling an enrolled factor and confirms the change", async ({ page }) => {
        await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { token: "test-token" } }));
        await page.route("**/api/v1/auth/sessions", route => route.fulfill({ json: [] }));
        await page.route("**/api/v1/auth/providers", route => route.fulfill({ json: { google: true } }));
        await page.route("**/api/v1/auth/google/status", route => route.fulfill({ json: { googleLinked: true } }));

        let enrolled = ["TOTP"];
        let recentlyAuthenticated = false;
        await page.route("**/api/v1/auth/mfa/status", route => route.fulfill({ json: { enrolled, recentlyAuthenticated } }));
        await page.route("**/api/v1/auth/account/reauth/password", async route => {
            expect(route.request().postDataJSON()).toEqual({ password: "current memorable passphrase" });
            recentlyAuthenticated = true;
            await route.fulfill({ status: 204 });
        });
        await page.route("**/api/v1/auth/mfa/disable", async route => {
            expect(route.request().postDataJSON()).toEqual({ kind: "TOTP", confirmed: true });
            enrolled = [];
            await route.fulfill({ status: 204 });
        });

        await page.goto("/account/security");
        await expect(page.getByRole("heading", { name: "Second-factor methods" })).toBeVisible();
        await expect(page.getByText("Verify your identity before changing a second factor.")).toBeVisible();
        await page.getByLabel("Password to verify your identity").fill("current memorable passphrase");
        await page.getByRole("button", { name: "Verify with password" }).click();
        await expect(page.getByText("Identity verified")).toBeVisible();

        await page.getByRole("button", { name: "Remove" }).click();
        await expect(page.getByRole("dialog", { name: "Remove this second factor?" })).toBeVisible();
        await page.getByRole("button", { name: "Remove second factor" }).click();
        await expect(page.getByText("Removed Authenticator app")).toBeVisible();
        await expect(page.getByText("No second factors are enrolled yet.")).toBeVisible();
    });
});
