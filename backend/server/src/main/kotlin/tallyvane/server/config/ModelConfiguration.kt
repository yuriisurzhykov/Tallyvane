package tallyvane.server.config

/**
 * How much of the model one person may use a day. Which provider and model answer, and with which key, is
 * chosen by an administrator in Admin, Models, not here.
 */
public class ModelConfiguration(public val tokensADay: Int) {
    override fun toString(): String = "ModelConfiguration(tokensADay=$tokensADay)"
}
