package tallyvane.identity.domain.secondfactor

/** Security action for which an authentication scheme grants proof. */
public enum class AuthenticationAction {
    SIGN_IN,
    CHANGE_PRIMARY_CREDENTIAL,
    MANAGE_SECOND_FACTORS,
}
