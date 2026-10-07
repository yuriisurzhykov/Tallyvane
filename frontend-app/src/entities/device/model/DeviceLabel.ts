import type { components } from "frontend-shared/api";

type Wire = components["schemas"]["Device"];

/** The words of a description that are not proper names, so that the screen's own language supplies them. */
export interface DeviceWords {
    readonly unknownDevice: string;
    readonly otherBrowser: string;
    readonly unknownSystem: string;
    readonly mobile: string;
    /** "Chrome on Windows": the two parts joined the way the language joins them. */
    readonly described: (browser: string, system: string) => string;
}

const BROWSERS: Readonly<Record<Exclude<Wire["browser"], "other">, string>> = {
    chrome: "Chrome",
    edge: "Edge",
    firefox: "Firefox",
    opera: "Opera",
    safari: "Safari",
};

const PLATFORMS: Readonly<Record<Exclude<Wire["platform"], "other">, string>> = {
    windows: "Windows",
    macos: "macOS",
    linux: "Linux",
    android: "Android",
    ios: "iOS",
    chromeos: "ChromeOS",
};

/**
 * What a browser said about itself, in parts, and what to call it: "Chrome on Windows", with "mobile" after it
 * for a phone or tablet. The devices page and the journal both name a device, and name it the same way.
 */
export class DeviceLabel {
    private readonly browser: Wire["browser"];
    private readonly platform: Wire["platform"];
    private readonly mobile: boolean;

    public constructor(browser: Wire["browser"], platform: Wire["platform"], mobile: boolean) {
        this.browser = browser;
        this.platform = platform;
        this.mobile = mobile;
    }

    public describe(words: DeviceWords): string {
        const browser = this.browser === "other" ? undefined : BROWSERS[this.browser];
        const system = this.platform === "other" ? undefined : PLATFORMS[this.platform];
        const kind = browser === undefined && system === undefined
            ? words.unknownDevice
            : words.described(browser ?? words.otherBrowser, system ?? words.unknownSystem);
        return this.mobile ? `${kind} · ${words.mobile}` : kind;
    }
}
