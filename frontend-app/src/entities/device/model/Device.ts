import type { components } from "frontend-shared/api";
import { DeviceLabel, type DeviceWords } from "./DeviceLabel";

type Wire = components["schemas"]["Device"];

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

/** The server keeps the last activity to the minute, so "now" cannot mean less than this. */
const ACTIVE_NOW_MS = 2 * MINUTE_MS;

/**
 * One place a person is signed in. The server says what the browser said about itself, in parts; what to
 * call it is decided here, in the person's language, and the name the person gave it comes first.
 */
export class Device {
    private readonly wire: Wire;
    private readonly label: DeviceLabel;

    public constructor(wire: Wire) {
        this.wire = wire;
        this.label = new DeviceLabel(wire.browser, wire.platform, wire.mobile);
    }

    /** What names this device to the server, and to React among its siblings. It is not a secret. */
    public key(): string {
        return this.wire.id;
    }

    public isCurrent(): boolean {
        return this.wire.current;
    }

    /** What the person called it, or an empty text when they have not. */
    public givenName(): string {
        return this.wire.name ?? "";
    }

    /** "Chrome on Windows", with "mobile" after it for a phone or tablet. */
    public description(words: DeviceWords): string {
        return this.label.describe(words);
    }

    public signedInOn(locale: string): string {
        return new Intl.DateTimeFormat(locale, { day: "numeric", month: "short", year: "numeric" }).format(
            new Date(this.wire.signed_in_at),
        );
    }

    public isActiveAt(now: Date): boolean {
        return now.getTime() - this.lastActive().getTime() < ACTIVE_NOW_MS;
    }

    /** "3 hours ago". For a device active a moment ago this is still a time, so ask [isActiveAt] first. */
    public lastActiveAgo(now: Date, locale: string): string {
        const elapsed = Math.max(0, now.getTime() - this.lastActive().getTime());
        const format = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });
        if (elapsed < HOUR_MS) {
            return format.format(-Math.floor(elapsed / MINUTE_MS), "minute");
        }
        if (elapsed < DAY_MS) {
            return format.format(-Math.floor(elapsed / HOUR_MS), "hour");
        }
        return format.format(-Math.floor(elapsed / DAY_MS), "day");
    }

    /** The exact time, for the place a person hovers to be sure. */
    public lastActiveOn(locale: string): string {
        return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(this.lastActive());
    }

    private lastActive(): Date {
        return new Date(this.wire.last_active_at);
    }
}
