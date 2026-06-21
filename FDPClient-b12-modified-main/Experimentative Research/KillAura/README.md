# KillAura — Experimental Research

This folder holds the KillAura (or its closest equivalent) code from three
Minecraft hacked clients, plus a detailed comparison and improvement plan.

## Layout

```
KillAura/
├── README.md                          # this file
├── _Comparison/
│   └── ANALYSIS.md                    # the full diff + improvement plan
├── CurrentClient/
│   ├── KillAura.kt                    # the current client's full KillAura
│   └── KillAuraTargeter.kt            # the visual targeter module
├── LiquidBounce/
│   ├── ModuleKillAura.kt              # the entry-point module
│   ├── KillAuraClicker.kt             # click timing / miss-time aware
│   ├── KillAuraTargetTracker.kt       # target enumeration
│   ├── KillAuraRotationsValueGroup.kt # rotation config
│   ├── KillAuraRequirements.kt        # bed / inventory gates
│   └── features/
│       ├── KillAuraAutoBlock.kt       # blink-aware autoblock
│       ├── KillAuraFailSwing.kt       # fake-swing on miss
│       ├── KillAuraFightBot.kt        # click logic
│       ├── KillAuraNotifyWhenFail.kt  # sound / box on miss
│       ├── KillAuraRange.kt           # randomized scan range
│       └── KillAuraRangeIndicator.kt  # render
├── Raven-bPLUS/
│   ├── AimAssist.java                 # the "KillAura" equivalent
│   ├── AutoBlock.java                 # auto-block (timed)
│   ├── AutoWeapon.java                # hotbar swap
│   ├── BurstClicker.java              # threaded drag-click
│   ├── ClickAssist.java               # click rate assist
│   ├── HitBox.java                    # reach extender
│   ├── LeftClicker.java               # auto left-click
│   ├── Velocity.java                  # knockback cancel
│   ├── WTap.java                      # W-tap combo
│   ├── Module.java                    # base module
│   └── ModuleManager.java             # module registry
└── _tools/
    └── extract_raven.py               # Python helper used to download
                                       # Raven-bPLUS from the Wayback
                                       # Machine (see _Comparison/ANALYSIS.md
                                       # for the DMCA / mirror notes)
```

## Where the code came from

| Source | Repository | Captured by |
|---|---|---|
| `LiquidBounce/` | `https://github.com/CCBlueX/LiquidBounce` (cloned to `Library/LiquidBounce`) | `git clone --depth 1` on 2026-06-18 |
| `Raven-bPLUS/` | `https://github.com/Kopamed/Raven-bPLUS` (DMCA-blocked since 2025-07-15) | `curl` against the Wayback Machine snapshot `20240823231313` of the `main` branch blob pages, parsed with `_tools/extract_raven.py` |
| `CurrentClient/` | `src/main/java/net/asd/union/features/module/modules/combat/KillAura.kt` and `KillAuraTargeter.kt` in this workspace | `cp` |

The full comparison and improvement plan is in
[`_Comparison/ANALYSIS.md`](_Comparison/ANALYSIS.md).
