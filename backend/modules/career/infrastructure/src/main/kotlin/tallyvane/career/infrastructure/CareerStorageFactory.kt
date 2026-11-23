package tallyvane.career.infrastructure

import tallyvane.career.application.port.Goals

public class CareerStorageFactory {
    public fun goals(): Goals = PostgresGoals()

    override fun toString(): String = "CareerStorageFactory(schema=career)"
}
