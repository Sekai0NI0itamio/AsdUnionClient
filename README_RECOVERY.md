# AsdUnion Recovery Notes

This repo is set up as a recovery workspace for `AsdUnion-b12.jar`.

## Layout

- `recovery/classfiles/`
  Extracted client-owned class files from the jar.
- `recovery/decompiled-grouped/`
  Preferred raw decompiler output. This keeps inner classes grouped with their top-level file when possible.
- `recovery/decompiled-src/`
  Earlier bulk decompile attempt. Some files exist here even when grouped recovery failed.
- `recovery/resources/`
  Raw resource extraction from the jar.
- `src/main/resources/`
  Live resources used by the rebuild.
- `src/main/java/`
  Clean patch area for classes you want to actively rebuild.

## Important Limitation

A lot of this jar was originally Kotlin. Decompiled Java from Kotlin is often not compile-clean on the first pass.

That is why the repo is split into:

- raw recovered sources in `recovery/`
- active buildable sources in `src/main/java/`

The intended workflow is:

1. Find the class you want in `recovery/decompiled-grouped/`.
2. Copy it into `src/main/java/`.
3. Clean up any bad decompiler identifiers or syntax.
4. Rebuild the jar overlay.

## Helper Commands

Promote a recovered class into the active source tree:

```bash
./scripts/promote-recovered-class.sh net.asd.union.FDPClient
```

Build an updated jar offline using the local Forge/Gradle caches already on this machine:

```bash
./scripts/gradle-offline.sh rebuildJar
```

The rebuilt jar is written to:

```text
build/libs/AsdUnion-b12-rebuilt.jar
```

## Build Strategy

This project does not try to recompile every recovered source file at once.

Instead it:

- keeps `AsdUnion-b12.jar` as the base artifact
- recompiles any cleaned classes/resources from `src/main`
- reobfuscates those patched outputs
- overlays them back into a rebuilt jar

That gives you a working path to recover and replace the client incrementally instead of blocking on every hard-to-decompile Kotlin class upfront.

