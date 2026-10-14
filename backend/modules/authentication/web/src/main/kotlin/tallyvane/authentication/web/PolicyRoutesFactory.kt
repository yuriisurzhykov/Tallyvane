package tallyvane.authentication.web

import tallyvane.authentication.application.ActivatePolicyVersionUseCase
import tallyvane.authentication.application.AddPolicyVersionUseCase
import tallyvane.authentication.application.ListPolicyVersionsUseCase
import tallyvane.authentication.application.PolicyWords
import tallyvane.authentication.application.PreviewPolicyVersionUseCase
import tallyvane.platform.http.RouteModule

/**
 * Hands out the routes of the administrators' policy screens (ADR-098), for the composition root to mount. The route
 * classes are `internal`; one takes one use case. All of them are served on the administrators' site only.
 */
public class PolicyRoutesFactory(private val words: PolicyWords) {
    /**
     * `GET /policy-history`: the versions of the policy of one purpose.
     */
    public fun history(list: ListPolicyVersionsUseCase): RouteModule =
        PolicyHistoryRoutes(list, words, PolicyHistoryProblems())

    /**
     * `POST /policy-versions`: proposing a new version.
     */
    public fun versions(add: AddPolicyVersionUseCase): RouteModule = PolicyVersionRoutes(add, PolicyVersionProblems())

    /**
     * `GET /policy-previews`: what putting a version in force would do.
     */
    public fun previews(preview: PreviewPolicyVersionUseCase): RouteModule =
        PolicyPreviewRoutes(preview, PolicyPreviewProblems())

    /**
     * `POST /policy-activations`: putting a version in force.
     */
    public fun activations(activate: ActivatePolicyVersionUseCase): RouteModule =
        PolicyActivationRoutes(activate, PolicyActivationProblems())

    override fun toString(): String = "PolicyRoutesFactory"
}
