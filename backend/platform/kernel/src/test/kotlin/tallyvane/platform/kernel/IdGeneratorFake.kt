package tallyvane.platform.kernel

internal class IdGeneratorFake(
    private var sequence: Int = 0,
) : IdGenerator {
    override fun next(): String {
        sequence += 1
        return "id-$sequence"
    }
}
