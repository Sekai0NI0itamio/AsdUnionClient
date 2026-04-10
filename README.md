# AsdUnionClient

This repository is the recovery workspace for the `AsdUnion` 1.8.9 Forge client.

## Layout

- `FDPClient-b12-modified-main/`
  Primary recovered source tree. GitHub Actions builds this directory with `./gradlew build`.
- `AsdUnion-b12.jar`
  Last recovered jar used as the reference artifact during recovery.
- `README_RECOVERY.md`
  Notes about the bytecode/resource recovery work that has been done locally.
- `src/`
  Additional extracted resources from the recovered jar that may still help during reconciliation.

## Notes

- Large local-only recovery artifacts such as raw decompile dumps and offline caches are intentionally ignored so the public repository stays manageable.
- The active remote CI workflow lives at `.github/workflows/build.yml`.
