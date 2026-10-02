# Dialog

A blocking interruption over Base UI's Dialog. Not for creation flows (`Drawer` is). Used only where the page cannot continue until the user acts — today, the "session ended, sign in again" prompt (ADR-084/089). Controlled (`open`), no close button: the caller decides how it ends.
