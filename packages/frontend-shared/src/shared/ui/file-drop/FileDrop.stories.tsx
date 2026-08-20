import { FileDrop, type FileDropProps } from "./FileDrop";

/** See `field/Field.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<FileDropProps> = {
    title: "Inputs/FileDrop",
    component: FileDrop,
};
export default meta;

// Idle, drag-over and "file selected" are all internal state this
// component owns itself (see its own README) rather than props — these
// stories show the one state genuinely reachable through props alone.
// Storybook's own interaction/play functions, once this package adopts
// them, are the right place to exercise the other two; out of scope here.
export const Idle: Story<FileDropProps> = {
    args: {
        label: "Drag and drop your résumé here",
        browseLabel: "Browse files",
        clearLabel: "Remove selected file",
        accept: ".pdf,.doc,.docx",
        onFileChange: () => {
        },
    },
};

export const Disabled: Story<FileDropProps> = {
    args: {
        label: "Drag and drop your résumé here",
        browseLabel: "Browse files",
        clearLabel: "Remove selected file",
        disabled: true,
        onFileChange: () => {
        },
    },
};
