package tallyvane.jobs.application.port

import java.sql.Connection

interface Jobs {
    class Cached {
        fun connect(): Connection? = null
    }
}
