import type { components } from "frontend-shared/api";
import { DeviceLabel, type DeviceWords } from "@/entities/device/@x/security-entry";

type Wire = components["schemas"]["SecurityEntry"];

export type EntryKind = Wire["kind"];

/** How much an entry asks to be looked at: `neutral` is routine, `attention` is worth a check, `danger` is someone else. */
export type EntryTone = "neutral" | "attention" | "danger";

/** The words of an entry that the screen's own language supplies. */
export interface EntryWords {
    readonly titles: Readonly<Record<EntryKind, string>>;
    /** "Recovery code used to sign in · 3 left". */
    readonly withCodesLeft: (title: string, count: number) => string;
    /** "Work laptop · Chrome on Windows". */
    readonly named: (name: string, device: string) => string;
    readonly newDevice: string;
    readonly securityChange: string;
    readonly stopped: string;
    readonly device: DeviceWords;
}

const TONES: Readonly<Record<EntryKind, EntryTone>> = {
    signed_in: "neutral",
    totp_turned_on: "neutral",
    totp_turned_off: "attention",
    recovery_code_spent: "attention",
    recovery_codes_reissued: "neutral",
    other_devices_signed_out: "attention",
    guessing_stopped: "danger",
};

/**
 * One thing that happened to the account's security. The server says what happened and which entries are
 * notable; what to call it, and how to name the device, is decided here, in the person's language.
 */
export class SecurityEntry {
    private readonly wire: Wire;

    public constructor(wire: Wire) {
        this.wire = wire;
    }

    public kind(): EntryKind {
        return this.wire.kind;
    }

    /** A first sign-in from a device is notable though signing in is not. */
    public tone(): EntryTone {
        return this.isFirstFromDevice() ? "attention" : TONES[this.wire.kind];
    }

    /** The one word that marks a notable entry, or nothing for a routine one. */
    public badge(words: EntryWords): string | undefined {
        if (this.tone() === "neutral") {
            return undefined;
        }
        if (this.wire.kind === "guessing_stopped") {
            return words.stopped;
        }
        return this.isFirstFromDevice() ? words.newDevice : words.securityChange;
    }

    public title(words: EntryWords): string {
        const title = words.titles[this.wire.kind];
        return this.wire.codes_left === undefined ? title : words.withCodesLeft(title, this.wire.codes_left);
    }

    /** Where it happened, as it was then, or nothing when no device is known. */
    public device(words: EntryWords): string | undefined {
        const { device } = this.wire;
        if (device === undefined) {
            return undefined;
        }
        const label = new DeviceLabel(device.browser, device.platform, device.mobile).describe(words.device);
        return device.name === undefined ? label : words.named(device.name, label);
    }

    /** The exact time, in the person's zone. */
    public occurredOn(locale: string): string {
        return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(
            new Date(this.wire.occurred_at),
        );
    }

    private isFirstFromDevice(): boolean {
        return this.wire.kind === "signed_in" && this.wire.first_from_device;
    }
}
