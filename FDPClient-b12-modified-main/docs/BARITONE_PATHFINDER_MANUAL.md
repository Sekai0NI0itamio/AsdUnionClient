# Baritone Pathfinder Manual

## What was ported

This client now includes a lightweight Baritone-style land pathfinder in:

- `src/main/java/net/asd/union/utils/pathing/BaritonePathing.kt`

The port keeps the core ideas from Baritone's pathfinding model:

- Goal-driven A* search
- Heuristic-based node scoring
- Movement primitives instead of raw coordinate flood-fill
- Best-effort partial path fallback when a full path cannot be found

This is an adaptation for Forge 1.8.9, not a full drop-in copy of modern Baritone. The implementation is intentionally scoped to loaded client terrain and player movement that this client can safely reproduce.

## Main types

### Goals

Use one of the built-in goal types:

- `BaritoneGoalBlock`
- `BaritoneGoalNear`
- `BaritoneGoalXZ`
- `BaritoneGoalYLevel`

Examples:

```kotlin
val exactGoal = BaritoneGoalBlock(BlockPos(100, 64, -20))
val nearbyGoal = BaritoneGoalNear(BlockPos(100, 64, -20), 1)
val xzGoal = BaritoneGoalXZ(100, -20)
```

### Pathfinder

Create a synchronous pathfinder for a world:

```kotlin
val pathfinder = BaritonePathfinder(mc.theWorld)
val result = pathfinder.search(startBlockPos, nearbyGoal)
```

`PathSearchResult` contains:

- `status`: `SUCCESS`, `PARTIAL`, or `FAILED`
- `path`: `BaritonePath?`
- `nodesExplored`
- `resolvedStart`
- `reason`

`BaritonePath` contains:

- `positions`: ordered `BlockPos` list
- `steps`: ordered `BaritonePathStep` list
- `totalCost`
- `nodesExplored`
- `complete`

### Navigation session

If you want movement inputs and look targets, use `BaritoneNavigationSession`:

```kotlin
val navigator = BaritoneNavigationSession()

navigator.updateGoal(
    goal = BaritoneGoalNear(BlockPos(100, 64, -20), 1),
    exactGoal = Vec3(100.5, 64.0, -19.5),
    successRadius = 0.75,
)

val command = navigator.tick(mc.theWorld, mc.thePlayer)
```

`PathFollowCommand` contains:

- `active`
- `status`
- `lookTarget`
- `moveForward`
- `moveStrafe`
- `jump`
- `sprint`
- `currentTarget`
- `usingPath`

Helper methods:

```kotlin
command.applyToKeybinds(mc.gameSettings)
command.applyToMovementInput(event.originalInput)
```

## Recommended usage patterns

### 1. Path calculation only

Use this when your module only needs a route:

```kotlin
val player = mc.thePlayer ?: return
val start = BlockPos(player.posX, player.entityBoundingBox.minY, player.posZ)
val pathfinder = BaritonePathfinder(mc.theWorld)
val result = pathfinder.search(start, BaritoneGoalNear(targetPos, 1))

if (result.status == PathSearchStatus.SUCCESS || result.status == PathSearchStatus.PARTIAL) {
    val path = result.path ?: return
    val nodes = path.positions
    val steps = path.steps
}
```

### 2. Full movement control

Use this when your module should actually walk the player:

```kotlin
private val navigator = BaritoneNavigationSession()
private var command = PathFollowCommand.idle()

val onUpdate = handler<UpdateEvent> {
    val player = mc.thePlayer ?: return@handler

    navigator.updateGoal(
        goal = BaritoneGoalNear(targetBlock, 1),
        exactGoal = targetVec,
        successRadius = 0.75,
    )

    command = navigator.tick(mc.theWorld, player)

    command.lookTarget?.let { look ->
        setTargetRotation(
            toRotation(Vec3(look.xCoord, player.eyes.yCoord, look.zCoord), false, player)
        )
    }

    command.applyToKeybinds(mc.gameSettings)
}

val onMovementInput = handler<MovementInputEvent> { event ->
    command.applyToMovementInput(event.originalInput)
}
```

### 3. Reset behavior

Always clear the navigator when your module stops owning movement:

```kotlin
navigator.clear()
command = PathFollowCommand.idle()
```

This should happen on:

- module disable
- world unload
- player death
- mode switch away from pathing

## Current movement model

The 1.8.9 port currently searches using these movement primitives:

- `WALK`
- `ASCEND`
- `DESCEND`
- `FALL`

This means the search is good for standard land movement around blocks, ledges, and one-block climbs.

## Important limitations

This is not full modern Baritone. Current limitations are intentional:

- Search is synchronous. Do not spam long-distance searches every tick.
- Only loaded terrain is considered pathable.
- Liquids are treated as blocked.
- Fences, walls, and similar awkward supports are rejected as landing nodes.
- The current model does not try to break blocks, place blocks, use buckets, or do parkour.
- Half-block terrain such as slabs and stairs is not fully modeled yet.

Because of that, the safest general-purpose goal for modules is usually:

```kotlin
BaritoneGoalNear(targetBlock, 1)
```

Then use `exactGoal` for the final sub-block correction if you need precise centering.

## Anti AFK integration

`AntiAFK` now uses this system for center return:

- It plans toward a `BaritoneGoalNear` around the captured center block.
- It follows the computed node path while rotating smoothly.
- When the path is exhausted, it uses the exact captured `Vec3` for final precision.
- If path search fails, it falls back to direct steering instead of freezing.

## Extension ideas

If you want to expand this later, the most natural next steps are:

- diagonal moves
- better stair/slab handling
- water-aware traversal
- async search worker
- optional path rendering
- block breaking/placement costs
