package tallyvane.identity.application.login

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import tallyvane.identity.application.admin.BootstrapAdminAccountsUseCase
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind

class AfterProvisioningSpec :
    StringSpec({
        "provisions administrators before exposing supported sign-in options" {
            var provisioned = false
            val bootstrap = object : BootstrapAdminAccountsUseCase {
                override suspend fun provisionConfiguredAdmins(): Int {
                    provisioned = true
                    return 1
                }
            }
            val options = object : ReadSignInOptionsUseCase {
                override suspend fun read(): Set<AuthenticationTokenKind> {
                    check(provisioned)
                    return setOf(AuthenticationTokenKind.PASSWORD, AuthenticationTokenKind.GOOGLE)
                }
            }

            ReadSignInOptionsUseCase.AfterProvisioning(bootstrap, options).read()
                .shouldContainExactly(AuthenticationTokenKind.PASSWORD, AuthenticationTokenKind.GOOGLE)
        }
    })
