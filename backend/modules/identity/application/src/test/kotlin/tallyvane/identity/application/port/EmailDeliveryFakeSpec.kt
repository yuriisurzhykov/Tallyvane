package tallyvane.identity.application.port

class EmailDeliveryFakeSpec : EmailDeliveryConformance() {
    override fun fresh(): EmailDelivery = EmailDeliveryFake()
}
