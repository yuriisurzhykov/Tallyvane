import type { ReactNode } from "react";
import Link from "next/link";
import { Icon } from "frontend-shared/ui/icon";
import { IconButton } from "frontend-shared/ui/icon-button";
import { Logo } from "frontend-shared/ui/logo";
import { ProductShell } from "frontend-shared/ui/product-shell";
import { useStrings } from "@/shared/i18n";

export interface ProductFrameProps {
    readonly children: ReactNode;
    /** The account menu, supplied by the page: a widget cannot import another widget. */
    readonly account: ReactNode;
    /** Pinned below the page, such as the composer. */
    readonly dock?: ReactNode;
    /** A screen one step inside another shows a way back to Home. @default false */
    readonly back?: boolean;
    readonly width?: "column" | "wide";
}

/**
 * The frame every signed-in page shares: the wordmark home, the résumé and the account in the corner, and a
 * way back when the page is one step inside another. There is no sidebar; see `ProductShell`.
 */
export function ProductFrame({ children, account, dock, back = false, width }: ProductFrameProps) {
    const t = useStrings("frame");
    const brand = useStrings("auth");
    return (
        <ProductShell
            skipLinkLabel={t("skip")}
            brand={
                <Link href="/today" aria-label={t("home")}>
                    <Logo text={brand("productName")} mark={brand("mark")} />
                </Link>
            }
            actions={
                <>
                    <IconButton tone="ghost" label={t("resume")} render={<Link href="/resume" />}>
                        <Icon name="folder" />
                    </IconButton>
                    {account}
                </>
            }
            {...(back
                ? {
                      back: (
                          <IconButton tone="neutral" label={t("back")} render={<Link href="/today" />}>
                              <Icon name="back" />
                          </IconButton>
                      ),
                  }
                : {})}
            {...(dock !== undefined ? { dock } : {})}
            {...(width !== undefined ? { width } : {})}
        >
            {children}
        </ProductShell>
    );
}
