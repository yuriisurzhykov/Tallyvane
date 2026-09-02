package tallyvane.playground.argon2

import de.mkammerer.argon2.Argon2Factory

/**
 * Whether `de.mkammerer:argon2-jvm-nolibs` — no native binary bundled — can actually find and
 * call the system's Argon2 library through JNA, on the same base image `backend/Dockerfile`
 * ships: `eclipse-temurin:21-jre` with `libargon2-1` installed, nothing else. See
 * `backend/playground/argon2/README.md` for the question this exists to answer and the real
 * output of running it.
 */
fun main() {
    val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
    val password = "correct password".toCharArray()
    val wrongPassword = "wrong password".toCharArray()

    val hash = argon2.hash(2, 19 * 1024, 1, password)
    println("hash: $hash")

    val matchesCorrect = argon2.verify(hash, password)
    val matchesWrong = argon2.verify(hash, wrongPassword)
    println("verify(correct password) = $matchesCorrect")
    println("verify(wrong password)   = $matchesWrong")

    argon2.wipeArray(password)
    argon2.wipeArray(wrongPassword)

    check(matchesCorrect) { "The correct password did not verify against its own hash." }
    check(!matchesWrong) { "A wrong password verified against a hash it should not match." }
    println("RESULT: native Argon2 loaded and both checks matched expectations")
}
