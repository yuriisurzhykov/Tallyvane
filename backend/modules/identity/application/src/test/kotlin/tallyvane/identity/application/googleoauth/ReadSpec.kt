package tallyvane.identity.application.googleoauth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.user.UserId
import kotlin.uuid.Uuid

class ReadSpec :
    StringSpec({
        "reads whether a Google credential is linked to the requested account" {
            val linked = UserId(Uuid.parse("00000000-0000-7000-8000-000000000047"))
            val unlinked = UserId(Uuid.parse("00000000-0000-7000-8000-000000000048"))
            val credentials = CredentialRepositoryFake().also {
                it.save(linked, Credential.GoogleRecord(GoogleSubject("google-subject")))
            }

            ReadGoogleAccountLinkUseCase.Read(credentials).isLinked(linked) shouldBe true
            ReadGoogleAccountLinkUseCase.Read(credentials).isLinked(unlinked) shouldBe false
        }
    })
