import { Button } from "frontend-shared/ui/button";
import { Dot } from "frontend-shared/ui/dot";
import { Link as TextLink } from "frontend-shared/ui/link";
import { Logo } from "frontend-shared/ui/logo";
import { Row } from "frontend-shared/ui/row";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import NextLink from "next/link";
import { useStrings } from "@/app/i18n";
import { LandingFaq } from "./LandingFaq";
import { Native, nativeRender } from "./native";
import styles from "./LandingPage.module.css";

const APP_URL = "https://app.surzhykov.icu";

export function LandingPage() {
    return (
        <Native as="div" className={styles.page ?? ""}>
            <Native as="div" className={styles.frame ?? ""}>
                <LandingHeader />
                <Native as="main" className={styles.main ?? ""}>
                    <HeroSection />
                    <WorkflowSection />
                    <FaqSection />
                    <ClosingSection />
                </Native>
                <LandingFooter />
            </Native>
        </Native>
    );
}

function LandingHeader() {
    const tCommon = useStrings("common");
    const t = useStrings("landing");

    return (
        <Native as="header" className={styles.header ?? ""}>
            <TextLink href="#top" aria-label={tCommon("productName")} className={`${styles.brand ?? ""} text-small`}>
                <Logo text={tCommon("productName")} />
            </TextLink>
            <Row as="nav" gap="group-gap" aria-label={t("primaryNavigation")} className={styles.navigation ?? ""}>
                <TextLink href="#how-it-works" className="text-small">{t("navHowItWorks")}</TextLink>
                <TextLink href="#faq" className="text-small">{t("navAbout")}</TextLink>
            </Row>
            <Row gap="group-gap" className={styles.headerActions ?? ""}>
                <TextLink href={`${APP_URL}/login`} className="text-small">{t("signIn")}</TextLink>
                <GetStartedButton label={t("getStarted")} size="sm" />
            </Row>
        </Native>
    );
}

function HeroSection() {
    const t = useStrings("landing");
    return (
        <Stack as="section" gap="section-gap" id="top" aria-labelledby="landing-title" className={styles.hero ?? ""}>
            <Stack gap="section-gap" className={styles.heroCopy ?? ""}>
                <Stack gap="stack" className={styles.heroText ?? ""}>
                    <Text variant="overline" color="muted" className={styles.eyebrow ?? ""}>{t("heroEyebrow")}</Text>
                    <Text id="landing-title" variant="hero" render={nativeRender("h1")} className={styles.heroTitle ?? ""}>
                        {t("heroTitle")}
                    </Text>
                    <Text variant="body" color="secondary" className={styles.heroDescription ?? ""}>
                        {t("heroDescription")}
                    </Text>
                </Stack>
                <Row gap="group-gap" className={styles.heroActions ?? ""}>
                    <GetStartedButton label={t("getStarted")} size="lg" />
                    <TextLink href="#how-it-works" className={`${styles.secondaryAction ?? ""} text-body`}>
                        {t("seeHowItWorks")}
                    </TextLink>
                </Row>
            </Stack>
            <CareerMemoryPreview />
        </Stack>
    );
}

function WorkflowSection() {
    const t = useStrings("landing");
    const steps = ["remember", "connect", "act"] as const;
    return (
        <Stack as="section" gap="section-gap" id="how-it-works" aria-labelledby="workflow-title" className={styles.workflow ?? ""}>
            <Stack gap="stack-tight" className={styles.sectionHeading ?? ""}>
                <Text variant="overline" color="muted" className={styles.eyebrow ?? ""}>{t("workflowEyebrow")}</Text>
                <Text id="workflow-title" variant="display" render={nativeRender("h2")}>{t("workflowTitle")}</Text>
                <Text variant="body" color="secondary">{t("workflowDescription")}</Text>
            </Stack>
            <Row gap="group-gap" className={styles.workflowGrid ?? ""}>
                {steps.map((step, index) => (
                    <Stack as="article" gap="stack" className={styles.workflowCard ?? ""} key={step}>
                        <Text variant="numeric" color="muted" className={styles.stepNumber ?? ""}>
                            {`0${String(index + 1)}`}
                        </Text>
                        <Text variant="title3" render={nativeRender("h3")}>{t(`${step}Title`)}</Text>
                        <Text variant="body" color="secondary">{t(`${step}Description`)}</Text>
                    </Stack>
                ))}
            </Row>
            <Row gap="inline" className={styles.capabilityLine ?? ""} aria-label={t("capabilitiesLabel")}>
                <Text variant="small" color="muted">{t("capabilityResume")}</Text>
                <Native as="span" ariaHidden>·</Native>
                <Text variant="small" color="muted">{t("capabilityFit")}</Text>
                <Native as="span" ariaHidden>·</Native>
                <Text variant="small" color="muted">{t("capabilityPay")}</Text>
                <Native as="span" ariaHidden>·</Native>
                <Text variant="small" color="muted">{t("capabilityOutcomes")}</Text>
            </Row>
        </Stack>
    );
}

function FaqSection() {
    const t = useStrings("landing");
    return (
        <Stack as="section" gap="stack" id="faq" aria-labelledby="faq-title" className={styles.faq ?? ""}>
            <Stack gap="stack-tight" className={styles.sectionHeading ?? ""}>
                <Text variant="overline" color="muted" className={styles.eyebrow ?? ""}>{t("faqEyebrow")}</Text>
                <Text id="faq-title" variant="display" render={nativeRender("h2")}>{t("faqTitle")}</Text>
            </Stack>
            <LandingFaq />
        </Stack>
    );
}

function ClosingSection() {
    const t = useStrings("landing");
    return (
        <Stack as="section" gap="group-gap" aria-labelledby="closing-title" className={styles.closing ?? ""}>
            <Stack gap="stack-tight">
                <Text variant="overline" color="muted" className={styles.eyebrow ?? ""}>{t("closingEyebrow")}</Text>
                <Text id="closing-title" variant="display" render={nativeRender("h2")}>{t("closingTitle")}</Text>
                <Text variant="body" color="secondary">{t("closingDescription")}</Text>
            </Stack>
            <GetStartedButton label={t("getStarted")} size="lg" />
        </Stack>
    );
}

function GetStartedButton({ label, size }: { label: string; size: "sm" | "lg" }) {
    return (
        <Button tone="primary" size={size} trailingIcon={<Text as="span" variant="body" aria-hidden="true">↗</Text>}
                render={<NextLink href={`${APP_URL}/register`} />} nativeButton={false}>
            {label}
        </Button>
    );
}

function LandingFooter() {
    const tCommon = useStrings("common");
    const t = useStrings("landing");
    return (
        <Native as="footer" className={styles.footer ?? ""}>
            <Logo text={tCommon("productName")} />
            <Text variant="small" color="muted">{t("footerTagline")}</Text>
            <Row gap="group-gap">
                <TextLink href="#top" className="text-small">{t("backToTop")}</TextLink>
                <TextLink href={`${APP_URL}/login`} className="text-small">{t("signIn")}</TextLink>
            </Row>
        </Native>
    );
}

function CareerMemoryPreview() {
    const t = useStrings("landing");
    return (
        <Stack as="aside" gap="group-gap" aria-label={t("productPreviewLabel")} className={styles.previewWrap ?? ""}>
            <Surface variant="primary" className={styles.preview ?? ""}>
                <PreviewTopbar />
                <Stack gap="group-gap" className={styles.previewBody ?? ""}>
                    <PreviewHeading />
                    <MemoryMap />
                    <NextStepPreview />
                </Stack>
            </Surface>
            <Text variant="caption" color="muted" className={styles.previewCaption ?? ""}>{t("previewCaption")}</Text>
        </Stack>
    );
}

function PreviewTopbar() {
    const t = useStrings("landing");
    return (
        <Row gap="inline" className={styles.previewTopbar ?? ""}>
            <Row gap="inline" aria-hidden="true" className={styles.windowDots ?? ""}>
                <Native as="span"/><Native as="span"/><Native as="span"/>
            </Row>
            <Text variant="caption" color="muted">{t("previewWorkspace")}</Text>
            <Text variant="caption" color="muted">{t("previewToday")}</Text>
        </Row>
    );
}

function PreviewHeading() {
    const t = useStrings("landing");
    return (
        <Row gap="group-gap" className={styles.previewHeading ?? ""}>
            <Stack gap="inline-tight">
                <Text variant="overline" color="muted">{t("previewEyebrow")}</Text>
                <Text variant="title2" render={nativeRender("h2")}>{t("previewTitle")}</Text>
            </Stack>
            <Text variant="small" color="muted" className={styles.previewCount ?? ""}>{t("previewExample")}</Text>
        </Row>
    );
}

function MemoryMap() {
    const t = useStrings("landing");
    return (
        <Native as="div" className={styles.memoryMap ?? ""} ariaLabel={t("memoryMapLabel")}>
            <Stack as="div" gap="inline" className={styles.memoryNodes ?? ""}>
                <MemoryNode label={t("memoryFactLabel")} detail={t("memoryFact")} primary />
                <Native as="span" className={styles.memoryEdge ?? ""} ariaHidden />
                <MemoryNode label={t("memorySkillLabel")} detail={t("memorySkill")} />
                <Native as="span" className={styles.memoryEdge ?? ""} ariaHidden />
                <MemoryNode label={t("memoryOutcomeLabel")} detail={t("memoryOutcome")} />
            </Stack>
            <Native as="div" className={styles.mapConnector ?? ""} ariaHidden>
                <Native as="span"/><Native as="span"/><Native as="span"/>
            </Native>
            <Surface variant="inset" className={styles.roleMatch ?? ""}>
                <Row gap="inline" className={styles.roleMatchHeading ?? ""}>
                    <Dot tone="success" />
                    <Text variant="overline" color="muted">{t("roleMatchLabel")}</Text>
                </Row>
                <Text variant="small">{t("roleMatchText")}</Text>
            </Surface>
        </Native>
    );
}

function MemoryNode({ label, detail, primary = false }: { label: string; detail: string; primary?: boolean }) {
    const className = primary
        ? `${styles.memoryNode ?? ""} ${styles.memoryNodePrimary ?? ""}`
        : styles.memoryNode ?? "";
    return (
        <Surface variant="inset" className={className}>
            <Text variant="overline" color="muted">{label}</Text>
            <Text variant="small">{detail}</Text>
        </Surface>
    );
}

function NextStepPreview() {
    const t = useStrings("landing");
    return (
        <Surface variant="inset" className={styles.nextStep ?? ""}>
            <Row gap="inline" className={styles.nextStepLabel ?? ""}>
                <Dot tone="attention" />
                <Text variant="overline" color="muted">{t("previewNextStep")}</Text>
            </Row>
            <Text variant="bodyStrong">{t("previewTask")}</Text>
            <Text variant="small" color="muted">{t("previewTaskDetail")}</Text>
        </Surface>
    );
}
