/* global test, expect */

import { classifyAdminAccessStatus, resolveAdminLoginReturnTo } from "./login-routing.ts";

void test("defaults the post-login destination to the admin pages screen", () => {
    expect(resolveAdminLoginReturnTo(null)).toBe("/pages");
});

void test("keeps safe internal return destinations", () => {
    expect(resolveAdminLoginReturnTo("/authentication?tab=policy")).toBe("/authentication?tab=policy");
});

void test("rejects external and protocol-relative return destinations", () => {
    expect(resolveAdminLoginReturnTo("https://example.com")).toBe("/pages");
    expect(resolveAdminLoginReturnTo("//example.com")).toBe("/pages");
    expect(resolveAdminLoginReturnTo("/\\\\example.com")).toBe("/pages");
});

void test("classifies protected admin API responses for the login gate", () => {
    expect(classifyAdminAccessStatus(200)).toBe("authorized");
    expect(classifyAdminAccessStatus(401)).toBe("unauthenticated");
    expect(classifyAdminAccessStatus(403)).toBe("denied");
    expect(classifyAdminAccessStatus(503)).toBe("unavailable");
});
