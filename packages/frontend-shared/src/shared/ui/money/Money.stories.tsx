import { Money, type MoneyProps } from "./Money";

/** See `panel/Panel.stories.tsx` for why this local shape stands in for CSF3's real types. */
interface StoryMeta<TProps> {
    readonly title: string;
    readonly component: (props: TProps) => React.ReactElement | null;
}

interface Story<TProps> {
    readonly args: TProps;
}

const meta: StoryMeta<MoneyProps> = {
    title: "Compounds/Money",
    component: Money,
};
export default meta;

export const Default: Story<MoneyProps> = { args: { cents: 425000 } };
export const Zero: Story<MoneyProps> = { args: { cents: 0 } };
export const Negative: Story<MoneyProps> = { args: { cents: -1999 } };
export const NonUsdCurrency: Story<MoneyProps> = { args: { cents: 999, currency: "EUR" } };
