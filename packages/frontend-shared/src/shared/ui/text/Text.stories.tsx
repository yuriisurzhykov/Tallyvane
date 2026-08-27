import { Text, type TextProps } from "./Text";

/**
 * `@storybook/react-vite`'s types live in `packages/storybook`'s own
 * devDependencies, not in this package's — importing them here would be a
 * cross-package type import this package cannot resolve, and adding the
 * dependency here is out of scope for this batch. This local shape covers
 * only what a CSF3 story file actually needs: a `{ title, component }`
 * default export and `{ args }` named exports.
 */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<TextProps> = {
    title: "Typography and marks/Text",
    component: Text,
};
export default meta;

export const Hero: Story<TextProps> = { args: { variant: "hero", children: "Hero" } };
export const Display: Story<TextProps> = { args: { variant: "display", children: "Display" } };
export const Title1: Story<TextProps> = { args: { variant: "title1", children: "Title 1" } };
export const Title2: Story<TextProps> = { args: { variant: "title2", children: "Title 2" } };
export const Title3: Story<TextProps> = { args: { variant: "title3", children: "Title 3" } };
export const Body: Story<TextProps> = { args: { variant: "body", children: "Body copy, the default" } };
export const BodyStrong: Story<TextProps> = { args: { variant: "bodyStrong", children: "Body copy, emphasised" } };
export const Small: Story<TextProps> = { args: { variant: "small", children: "Dense tables and metadata" } };
export const Caption: Story<TextProps> = { args: { variant: "caption", children: "Captions and footnotes" } };
export const Overline: Story<TextProps> = { args: { variant: "overline", children: "Small capitalised heading" } };
export const Numeric: Story<TextProps> = { args: { variant: "numeric", children: "$185,000" } };

export const ColorSecondary: Story<TextProps> = { args: { variant: "body", color: "secondary", children: "Secondary colour" } };
export const ColorMuted: Story<TextProps> = { args: { variant: "body", color: "muted", children: "Muted colour" } };
export const ToneInfo: Story<TextProps> = { args: { variant: "body", tone: "info", children: "Info tone" } };
export const ToneAttention: Story<TextProps> = { args: { variant: "body", tone: "attention", children: "Attention tone" } };
export const ToneSuccess: Story<TextProps> = { args: { variant: "body", tone: "success", children: "Success tone" } };
export const ToneDanger: Story<TextProps> = { args: { variant: "body", tone: "danger", children: "Danger tone" } };

export const AsPageHeading: Story<TextProps> = {
    args: { variant: "title1", render: <h1 />, children: "Explicit page heading via render" },
};
