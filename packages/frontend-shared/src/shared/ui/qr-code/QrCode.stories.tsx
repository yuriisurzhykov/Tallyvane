import { QrCode } from "./QrCode";

/** See `Logo.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta {
    readonly title: string;
    readonly component: typeof QrCode;
}

interface Story {
    readonly args: {
        readonly value: string;
        readonly label: string;
        readonly className?: string;
    };
    /** No visible text — a QR code is a picture. Opts out of the APCA suite's text-contrast check, which has nothing to measure here. */
    readonly tags?: readonly string[];
}

const meta: StoryMeta = {
    title: "Shared/UI/QrCode",
    component: QrCode,
};

export default meta;

export const Default: Story = {
    args: {
        value: "otpauth://totp/Tallyvane?secret=JBSWY3DPEHPK3PXP&issuer=Tallyvane",
        label: "QR code for your authenticator app",
        className: "w-48",
    },
    tags: ["no-visible-text"],
};
