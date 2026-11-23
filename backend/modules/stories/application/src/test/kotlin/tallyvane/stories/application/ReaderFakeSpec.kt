package tallyvane.stories.application

import tallyvane.stories.application.port.Reader

class ReaderFakeSpec : ReaderConformance() {
    override fun fresh(): Reader = ReaderFake()
}
