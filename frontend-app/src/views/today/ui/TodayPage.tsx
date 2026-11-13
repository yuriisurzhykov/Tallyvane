"use client";

import { HomeBoard } from "@/widgets/home-board";
import { AccountMenu } from "@/widgets/account-menu";
import { ProductFrame } from "@/widgets/product-frame";

/** The home of variant E: the route, one main step, the stories, and the input pinned below. */
export function TodayPage() {
    return <HomeBoard frame={({ dock, children }) => <ProductFrame dock={dock} account={<AccountMenu />}>{children}</ProductFrame>} />;
}
