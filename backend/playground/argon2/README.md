# argon2

## 2026-09-02 — does `argon2-jvm-nolibs` actually find the system's Argon2 library on the image `backend/Dockerfile` ships?

`de.mkammerer:argon2-jvm-nolibs` bundles no native binary — the maintainer's own recommended
shape, taken over `argon2-jvm` (which bundles binaries for eight platforms) so this image ships
none. That shifts the risk from "wrong platform's binary loaded" to "JNA cannot find the library
at all", specifically because Debian/Ubuntu's `libargon2-1` package installs only the
SONAME-versioned file, `libargon2.so.1` — not the unversioned `libargon2.so` symlink, which only
the `-dev` package adds. JNA's own source names a `matchLibrary()` fallback for exactly this
shape ("deal with the case where `/usr/lib/libc.so` does not exist, or is not a valid symlink to
a versioned file"), but a fallback documented in a library's source is not the same as watching it
actually load on the exact image this project ships. Neither `./gradlew check` nor
`./gradlew :playground:argon2:run` on a development machine answers this: this machine is
Windows, and CI's own runner is not the container `backend/Dockerfile` builds.

So the image itself is the thing to run this against. `backend/Dockerfile`'s own step —
`apt-get install --no-install-recommends --yes libargon2-1` on `eclipse-temurin:21-jre` — was
copied into a throwaway `Dockerfile.verify` for this run only, not kept: keeping a second
Dockerfile that has to stay in sync with the real one is a worse record than reproducing the two
commands here.

Reproduce:

```bash
./gradlew :playground:argon2:installDist

# From backend/playground/argon2/, with a Dockerfile.verify holding:
#   FROM eclipse-temurin:21-jre
#   RUN apt-get update && apt-get install --no-install-recommends --yes libargon2-1 \
#       && rm -rf /var/lib/apt/lists/*
#   COPY build/install/argon2 /opt/argon2
#   CMD ["/opt/argon2/bin/argon2"]
docker build -f Dockerfile.verify -t argon2-verify .
docker run --rm argon2-verify
```

## What the run showed

```
hash: $argon2id$v=19$m=19456,t=2,p=1$JqUwvAXMxniAqPtf1Y1tVg$uEkfhti6F97WA1yDNgpFnOLZkFEJvdpDwReewAO3LQM
verify(correct password) = true
verify(wrong password)   = false
RESULT: native Argon2 loaded and both checks matched expectations
```

No `UnsatisfiedLinkError`, and both directions of `verify` answered correctly — the specific two
things that could plausibly have gone wrong (native library not found at all; found but the
binding calling it incorrectly) both didn't. `apt-get install libargon2-1` finished in 10 seconds
against a freshly pulled `eclipse-temurin:21-jre`, which is the same base image
`backend/Dockerfile` builds from, so the real Dockerfile's own step is what this result is about,
not a lookalike.

Not answered here, and not needed for this pass: performance under this server's actual load —
§1.5's ~250 ms target for the hash call is a separate measurement, against real hardware, once
`identity` has a first real caller for it.
