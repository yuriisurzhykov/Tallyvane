package tallyvane.arch

import com.lemonappdev.konsist.api.container.KoScope

internal data class ArchRule(
    val id: String,
    val scope: () -> KoScope = { productionScope() },
    val violations: (KoScope) -> List<String>,
)

internal val ARCH_RULES: List<ArchRule> =
    listOf(
        ArchRule("domain-is-pure", violations = ::domainIsPure),
        ArchRule("domain-no-application", violations = ::domainNoApplication),
        ArchRule("contract-is-self-contained", violations = ::contractIsSelfContained),
        ArchRule("contract-is-immutable", violations = ::contractIsImmutable),
        ArchRule("platform-knows-no-business", violations = ::platformKnowsNoBusiness),
        ArchRule("web-has-no-persistence", violations = ::webHasNoPersistence),
        ArchRule("single-public-method", violations = ::singlePublicMethod),
        ArchRule("usecase-is-imperative", violations = ::usecaseIsImperative),
        ArchRule("port-is-interface", violations = ::portIsInterface),
        ArchRule("port-naming", violations = ::portNaming),
        ArchRule("adapter-is-internal", violations = ::adapterIsInternal),
        ArchRule("no-banned-suffix", violations = ::noBannedSuffix),
        ArchRule("no-top-level-functions", violations = ::noTopLevelFunctions),
        ArchRule("no-stateful-objects", violations = ::noStatefulObjects),
        ArchRule("no-companion-logic", violations = ::noCompanionLogic),
        ArchRule("no-hardcoded-product-name", violations = ::noHardcodedProductName),
        ArchRule("no-ambient-time", violations = ::noAmbientTime),
        ArchRule("no-ambient-random", violations = ::noAmbientRandom),
        ArchRule("no-sql-concat", violations = ::noSqlConcat),
        ArchRule("own-schema-only", violations = ::ownSchemaOnly),
        ArchRule("no-cross-schema-join", violations = ::noCrossSchemaJoin),
        ArchRule("no-llm-with-personal-data", violations = ::noLlmWithPersonalData),
        ArchRule("port-has-contract-suite", scope = { codeScope() }, violations = ::portHasContractSuite),
        ArchRule("usecase-has-test", scope = { codeScope() }, violations = ::usecaseHasTest),
        ArchRule("registry-owns-branching", violations = ::registryOwnsBranching),
        ArchRule("app-has-no-logic", violations = ::appHasNoLogic),
        ArchRule("one-top-level-class", violations = ::oneTopLevelClass),
        ArchRule("package-matches-layer", violations = ::packageMatchesLayer),
        ArchRule("domain-no-annotations", violations = ::domainNoAnnotations),
        ArchRule("domain-no-var", violations = ::domainNoVar),
        ArchRule("contract-no-logic", violations = ::contractNoLogic),
        ArchRule("no-impl-suffix", violations = ::noImplSuffix),
        ArchRule("nested-impl-is-pure", violations = ::nestedImplIsPure),
        ArchRule("adapter-named-by-mechanism", violations = ::adapterNamedByMechanism),
        ArchRule("no-fake-in-main", violations = ::noFakeInMain),
        ArchRule("no-mock-libraries", scope = { codeScope() }, violations = ::noMockLibraries),
        ArchRule("no-di-framework", scope = { codeScope() }, violations = ::noDiFramework),
        ArchRule("web-one-usecase", violations = ::webOneUsecase),
    )

internal val KNOWN_RULES: Set<String> = ARCH_RULES.map { it.id }.toSet()
