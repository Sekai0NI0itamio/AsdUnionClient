package net.asd.union.utils.pathing

import net.minecraft.block.Block
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockCactus
import net.minecraft.block.BlockFence
import net.minecraft.block.BlockFenceGate
import net.minecraft.block.BlockFire
import net.minecraft.block.BlockLiquid
import net.minecraft.block.BlockSoulSand
import net.minecraft.block.BlockWall
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.gui.GuiDownloadTerrain
import net.minecraft.client.gui.GuiGameOver
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import net.minecraft.world.World
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object BaritoneActionCosts {
    const val COST_INF = 1_000_000.0
    const val WALK_ONE_BLOCK_COST = 20.0 / 4.317
    const val WALK_ONE_DIAGONAL_COST = WALK_ONE_BLOCK_COST * 1.41
    const val SPRINT_MULTIPLIER = 0.769
    const val WALK_OFF_BLOCK_COST = WALK_ONE_BLOCK_COST * 0.8
    const val CENTER_AFTER_FALL_COST = WALK_ONE_BLOCK_COST - WALK_OFF_BLOCK_COST
    const val LANDING_PENALTY = 0.35
    const val PARKOUR_JUMP_PENALTY = 1.15

    private fun velocity(ticks: Int): Double = (Math.pow(0.98, ticks.toDouble()) - 1.0) * -3.92

    private fun distanceToTicks(distance: Double): Double {
        if (distance == 0.0) {
            return 0.0
        }

        var remaining = distance
        var tickCount = 0

        while (true) {
            val fallDistance = velocity(tickCount)
            if (remaining <= fallDistance) {
                return tickCount + remaining / fallDistance
            }

            remaining -= fallDistance
            tickCount++
        }
    }

    val jumpOneBlockCost: Double = distanceToTicks(1.25) - distanceToTicks(0.25)

    fun fallCost(blocks: Int): Double = distanceToTicks(blocks.toDouble())
}

interface BaritoneGoal {
    fun isInGoal(x: Int, y: Int, z: Int): Boolean

    fun heuristic(x: Int, y: Int, z: Int): Double

    fun isInGoal(pos: BlockPos): Boolean = isInGoal(pos.x, pos.y, pos.z)

    fun heuristic(pos: BlockPos): Double = heuristic(pos.x, pos.y, pos.z)
}

data class BaritoneGoalBlock(val x: Int, val y: Int, val z: Int) : BaritoneGoal {
    constructor(pos: BlockPos) : this(pos.x, pos.y, pos.z)

    override fun isInGoal(x: Int, y: Int, z: Int): Boolean = x == this.x && y == this.y && z == this.z

    override fun heuristic(x: Int, y: Int, z: Int): Double {
        val xDiff = x - this.x
        val yDiff = y - this.y
        val zDiff = z - this.z
        return calculate(xDiff.toDouble(), yDiff, zDiff.toDouble())
    }

    companion object {
        fun calculate(xDiff: Double, yDiff: Int, zDiff: Double): Double {
            return BaritoneGoalYLevel.calculate(0, yDiff) + BaritoneGoalXZ.calculate(xDiff, zDiff)
        }
    }
}

data class BaritoneGoalNear(val x: Int, val y: Int, val z: Int, val range: Int) : BaritoneGoal {
    constructor(pos: BlockPos, range: Int) : this(pos.x, pos.y, pos.z, range)

    private val rangeSq = range * range

    override fun isInGoal(x: Int, y: Int, z: Int): Boolean {
        val xDiff = x - this.x
        val yDiff = y - this.y
        val zDiff = z - this.z
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= rangeSq
    }

    override fun heuristic(x: Int, y: Int, z: Int): Double {
        val xDiff = x - this.x
        val yDiff = y - this.y
        val zDiff = z - this.z
        return BaritoneGoalBlock.calculate(xDiff.toDouble(), yDiff, zDiff.toDouble())
    }
}

data class BaritoneGoalXZ(val x: Int, val z: Int) : BaritoneGoal {
    constructor(pos: BlockPos) : this(pos.x, pos.z)

    override fun isInGoal(x: Int, y: Int, z: Int): Boolean = x == this.x && z == this.z

    override fun heuristic(x: Int, y: Int, z: Int): Double = calculate((x - this.x).toDouble(), (z - this.z).toDouble())

    companion object {
        private val SQRT_2 = sqrt(2.0)

        fun calculate(xDiff: Double, zDiff: Double): Double {
            val x = abs(xDiff)
            val z = abs(zDiff)
            val straight = abs(x - z)
            val diagonal = minOf(x, z) * SQRT_2
            return diagonal * BaritoneActionCosts.WALK_ONE_DIAGONAL_COST + straight * BaritoneActionCosts.WALK_ONE_BLOCK_COST
        }
    }
}

data class BaritoneGoalYLevel(val level: Int) : BaritoneGoal {
    override fun isInGoal(x: Int, y: Int, z: Int): Boolean = y == level

    override fun heuristic(x: Int, y: Int, z: Int): Double = calculate(level, y)

    companion object {
        fun calculate(goalY: Int, currentY: Int): Double {
            return when {
                currentY > goalY -> BaritoneActionCosts.fallCost(currentY - goalY)
                currentY < goalY -> (goalY - currentY) * BaritoneActionCosts.jumpOneBlockCost
                else -> 0.0
            }
        }
    }
}

enum class PathMoveType {
    WALK,
    DIAGONAL,
    ASCEND,
    DIAGONAL_ASCEND,
    DESCEND,
    DIAGONAL_DESCEND,
    FALL,
    PARKOUR,
}

data class BaritonePathStep(
    val from: BlockPos,
    val to: BlockPos,
    val type: PathMoveType,
    val cost: Double,
)

data class BaritonePath(
    val positions: List<BlockPos>,
    val steps: List<BaritonePathStep>,
    val totalCost: Double,
    val nodesExplored: Int,
    val complete: Boolean,
)

enum class PathSearchStatus {
    SUCCESS,
    PARTIAL,
    FAILED,
}

data class PathSearchResult(
    val status: PathSearchStatus,
    val path: BaritonePath?,
    val nodesExplored: Int,
    val resolvedStart: BlockPos?,
    val reason: String? = null,
)

data class PathfinderSettings(
    val maxNodes: Int = 8_192,
    val maxFallDistance: Int = 4,
    val startResolveHorizontalRange: Int = 2,
    val startResolveVerticalRange: Int = 3,
    val allowParkour: Boolean = true,
    val maxParkourDistance: Int = 4,
    val allowDiagonal: Boolean = true,
    val allowDiagonalAscend: Boolean = true,
    val allowDiagonalDescend: Boolean = true,
)

private enum class PathDirection(val dx: Int, val dz: Int) {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0),
    NORTH_EAST(1, -1),
    NORTH_WEST(-1, -1),
    SOUTH_EAST(1, 1),
    SOUTH_WEST(-1, 1);

    val diagonal: Boolean
        get() = dx != 0 && dz != 0
}

class BaritonePathfinder(
    private val world: World,
    private val settings: PathfinderSettings = PathfinderSettings(),
) {

    private data class NodeRecord(
        val pos: BlockPos,
        val heuristic: Double,
        var cost: Double = BaritoneActionCosts.COST_INF,
        var combinedCost: Double = BaritoneActionCosts.COST_INF,
        var previous: NodeRecord? = null,
        var previousCost: Double = 0.0,
        var previousMoveType: PathMoveType? = null,
        var closed: Boolean = false,
    )

    private data class OpenEntry(
        val combinedCost: Double,
        val sequence: Long,
        val node: NodeRecord,
    ) : Comparable<OpenEntry> {
        override fun compareTo(other: OpenEntry): Int {
            val primary = combinedCost.compareTo(other.combinedCost)
            return if (primary != 0) primary else sequence.compareTo(other.sequence)
        }
    }

    private data class Transition(
        val destination: BlockPos,
        val cost: Double,
        val type: PathMoveType,
    )

    fun search(start: BlockPos, goal: BaritoneGoal): PathSearchResult {
        val resolvedStart = resolveNearestNode(start)
            ?: return PathSearchResult(PathSearchStatus.FAILED, null, 0, null, "Unable to resolve a valid start node from $start")

        if (goal.isInGoal(resolvedStart)) {
            return PathSearchResult(
                status = PathSearchStatus.SUCCESS,
                path = BaritonePath(listOf(resolvedStart), emptyList(), 0.0, 0, true),
                nodesExplored = 0,
                resolvedStart = resolvedStart,
            )
        }

        val nodeMap = HashMap<Long, NodeRecord>()
        val openSet = PriorityQueue<OpenEntry>()
        var sequence = 0L
        var nodesExplored = 0

        fun getNode(pos: BlockPos): NodeRecord {
            val key = pack(pos.x, pos.y, pos.z)
            return nodeMap.getOrPut(key) {
                NodeRecord(pos = pos, heuristic = goal.heuristic(pos))
            }
        }

        val startNode = getNode(resolvedStart)
        startNode.cost = 0.0
        startNode.combinedCost = startNode.heuristic
        openSet += OpenEntry(startNode.combinedCost, sequence++, startNode)

        var bestNode = startNode

        while (openSet.isNotEmpty() && nodesExplored < settings.maxNodes) {
            val entry = openSet.poll()
            val current = entry.node

            if (current.closed || entry.combinedCost > current.combinedCost) {
                continue
            }

            current.closed = true
            nodesExplored++

            if (goal.isInGoal(current.pos)) {
                return PathSearchResult(
                    status = PathSearchStatus.SUCCESS,
                    path = buildPath(current, nodesExplored, complete = true),
                    nodesExplored = nodesExplored,
                    resolvedStart = resolvedStart,
                )
            }

            if (isBetterCandidate(current, bestNode)) {
                bestNode = current
            }

            for (transition in collectTransitions(current.pos)) {
                val neighbor = getNode(transition.destination)
                val tentativeCost = current.cost + transition.cost

                if (tentativeCost + 1.0E-6 >= neighbor.cost) {
                    continue
                }

                neighbor.previous = current
                neighbor.previousCost = transition.cost
                neighbor.previousMoveType = transition.type
                neighbor.cost = tentativeCost
                neighbor.combinedCost = tentativeCost + neighbor.heuristic
                neighbor.closed = false
                openSet += OpenEntry(neighbor.combinedCost, sequence++, neighbor)
            }
        }

        val partialPath = bestNode.takeIf { it !== startNode }?.let { buildPath(it, nodesExplored, complete = false) }
        return PathSearchResult(
            status = if (partialPath != null) PathSearchStatus.PARTIAL else PathSearchStatus.FAILED,
            path = partialPath,
            nodesExplored = nodesExplored,
            resolvedStart = resolvedStart,
            reason = if (nodesExplored >= settings.maxNodes) {
                "Search stopped after reaching the node limit (${settings.maxNodes})"
            } else {
                "No valid route was found"
            },
        )
    }

    fun resolveNearestNode(origin: BlockPos): BlockPos? {
        if (isStandableNode(origin)) {
            return origin
        }

        var best: BlockPos? = null
        var bestDistanceSq = Double.POSITIVE_INFINITY

        for (dx in -settings.startResolveHorizontalRange..settings.startResolveHorizontalRange) {
            for (dz in -settings.startResolveHorizontalRange..settings.startResolveHorizontalRange) {
                for (dy in -settings.startResolveVerticalRange..settings.startResolveVerticalRange) {
                    val candidate = origin.add(dx, dy, dz)
                    if (!isStandableNode(candidate)) {
                        continue
                    }

                    val distanceSq = dx * dx + dy * dy + dz * dz.toDouble()
                    if (distanceSq < bestDistanceSq) {
                        best = candidate
                        bestDistanceSq = distanceSq
                    }
                }
            }
        }

        return best
    }

    fun isStandableNode(pos: BlockPos): Boolean {
        return isPassable(pos) && isPassable(pos.up()) && canStandOn(pos.down())
    }

    private fun collectTransitions(origin: BlockPos): List<Transition> {
        val transitions = ArrayList<Transition>(24)

        CARDINAL_DIRECTIONS.forEach { direction ->
            transitions += collectCardinalTransitions(origin, direction)
            if (settings.allowParkour) {
                transitions += collectParkourTransitions(origin, direction)
            }
        }

        if (settings.allowDiagonal) {
            DIAGONAL_DIRECTIONS.forEach { direction ->
                transitions += collectDiagonalTransitions(origin, direction)
            }
        }

        return transitions
    }

    private fun collectCardinalTransitions(origin: BlockPos, direction: PathDirection): List<Transition> {
        val targetX = origin.x + direction.dx
        val targetZ = origin.z + direction.dz
        if (!isLoadedColumn(targetX, targetZ)) {
            return emptyList()
        }

        val transitions = ArrayList<Transition>(1 + settings.maxFallDistance + 1)
        val sameLevel = BlockPos(targetX, origin.y, targetZ)

        if (isStandableNode(sameLevel)) {
            transitions += Transition(sameLevel, traverseCost(false, sprint = true), PathMoveType.WALK)
        }

        val ascend = BlockPos(targetX, origin.y + 1, targetZ)
        if (canAscend(origin, ascend, diagonal = false)) {
            transitions += Transition(ascend, ascendCost(false), PathMoveType.ASCEND)
        }

        transitions += collectDescendTransitions(origin, direction, diagonal = false)
        return transitions
    }

    private fun collectDiagonalTransitions(origin: BlockPos, direction: PathDirection): List<Transition> {
        val targetX = origin.x + direction.dx
        val targetZ = origin.z + direction.dz
        if (!isLoadedColumn(targetX, targetZ)) {
            return emptyList()
        }

        if (!diagonalCornerClear(origin, direction, origin.y)) {
            return emptyList()
        }

        val transitions = ArrayList<Transition>(1 + settings.maxFallDistance + 1)
        val sameLevel = BlockPos(targetX, origin.y, targetZ)

        if (isStandableNode(sameLevel) && isPassable(BlockPos(targetX, origin.y + 1, targetZ))) {
            transitions += Transition(sameLevel, traverseCost(true, sprint = true), PathMoveType.DIAGONAL)
        }

        val ascend = BlockPos(targetX, origin.y + 1, targetZ)
        if (settings.allowDiagonalAscend && canAscend(origin, ascend, diagonal = true)) {
            transitions += Transition(ascend, ascendCost(true), PathMoveType.DIAGONAL_ASCEND)
        }

        if (settings.allowDiagonalDescend) {
            transitions += collectDescendTransitions(origin, direction, diagonal = true)
        }

        return transitions
    }

    private fun collectDescendTransitions(origin: BlockPos, direction: PathDirection, diagonal: Boolean): List<Transition> {
        val transitions = ArrayList<Transition>(settings.maxFallDistance)
        val targetX = origin.x + direction.dx
        val targetZ = origin.z + direction.dz

        for (fallDistance in 1..settings.maxFallDistance) {
            val descend = BlockPos(targetX, origin.y - fallDistance, targetZ)
            if (descend.y < 1 || !isLoadedColumn(descend.x, descend.z)) {
                continue
            }

            if (!canFallInto(origin, descend, diagonal, direction)) {
                continue
            }

            transitions += Transition(
                destination = descend,
                cost = descendCost(fallDistance, diagonal),
                type = when {
                    diagonal && fallDistance == 1 -> PathMoveType.DIAGONAL_DESCEND
                    diagonal -> PathMoveType.FALL
                    fallDistance == 1 -> PathMoveType.DESCEND
                    else -> PathMoveType.FALL
                }
            )
        }

        return transitions
    }

    private fun collectParkourTransitions(origin: BlockPos, direction: PathDirection): List<Transition> {
        val maxDistance = settings.maxParkourDistance.coerceIn(2, 4)
        if (!settings.allowParkour || !isPassable(origin.up(2)) || !canSprintFrom(origin)) {
            return emptyList()
        }

        val transitions = ArrayList<Transition>(maxDistance)
        for (distance in 2..maxDistance) {
            val destination = BlockPos(origin.x + direction.dx * distance, origin.y, origin.z + direction.dz * distance)
            if (!isLoadedColumn(destination.x, destination.z)) {
                break
            }

            if (isParkourLandingClear(origin, direction, destination, distance, ascend = false)) {
                transitions += Transition(destination, parkourCost(distance, false), PathMoveType.PARKOUR)
                continue
            }

            val ascendLanding = destination.up()
            if (distance <= 3 && isParkourLandingClear(origin, direction, ascendLanding, distance, ascend = true)) {
                transitions += Transition(ascendLanding, parkourCost(distance, true), PathMoveType.PARKOUR)
            }
        }

        return transitions
    }

    private fun canAscend(origin: BlockPos, destination: BlockPos, diagonal: Boolean): Boolean {
        if (!isLoadedColumn(destination.x, destination.z)) {
            return false
        }

        if (!isStandableNode(destination)) {
            return false
        }

        if (!isPassable(origin.up(2)) || !isPassable(destination.up())) {
            return false
        }

        if (diagonal) {
            return diagonalCornerClear(origin, diagonalDirection(origin, destination), origin.y) &&
                diagonalCornerClear(origin, diagonalDirection(origin, destination), origin.y + 1)
        }

        return true
    }

    private fun canFallInto(origin: BlockPos, destination: BlockPos, diagonal: Boolean, direction: PathDirection): Boolean {
        if (!isStandableNode(destination)) {
            return false
        }

        if (diagonal && !diagonalCornerClear(origin, direction, destination.y)) {
            return false
        }

        val minY = destination.y
        val maxY = origin.y
        for (y in minY..maxY) {
            val pos = BlockPos(destination.x, y, destination.z)
            if (!isPassable(pos) || !isPassable(pos.up())) {
                return false
            }
        }

        return true
    }

    private fun diagonalCornerClear(origin: BlockPos, direction: PathDirection, y: Int): Boolean {
        val sideA = BlockPos(origin.x + direction.dx, y, origin.z)
        val sideB = BlockPos(origin.x, y, origin.z + direction.dz)
        val sideAHead = sideA.up()
        val sideBHead = sideB.up()
        return isPassable(sideA) && isPassable(sideB) && isPassable(sideAHead) && isPassable(sideBHead)
    }

    private fun diagonalDirection(origin: BlockPos, destination: BlockPos): PathDirection {
        val dx = (destination.x - origin.x).coerceIn(-1, 1)
        val dz = (destination.z - origin.z).coerceIn(-1, 1)
        return DIAGONAL_DIRECTIONS.first { it.dx == dx && it.dz == dz }
    }

    private fun isParkourLandingClear(
        origin: BlockPos,
        direction: PathDirection,
        destination: BlockPos,
        distance: Int,
        ascend: Boolean,
    ): Boolean {
        if (!isStandableNode(destination)) {
            return false
        }

        val landingBase = if (ascend) destination.down() else destination
        if (!isPassable(landingBase.up()) || !isPassable(landingBase.up(2))) {
            return false
        }

        for (step in 1 until distance) {
            val flyPos = BlockPos(origin.x + direction.dx * step, origin.y, origin.z + direction.dz * step)
            if (!isPassable(flyPos) || !isPassable(flyPos.up()) || !isPassable(flyPos.up(2))) {
                return false
            }

            if (canStandOn(flyPos.down())) {
                return false
            }
        }

        val overshootX = destination.x + direction.dx
        val overshootZ = destination.z + direction.dz
        val overshootFeet = BlockPos(overshootX, destination.y, overshootZ)
        val overshootHead = overshootFeet.up()
        return isPassable(overshootFeet) && isPassable(overshootHead) && !isHazard(overshootFeet.down())
    }

    private fun traverseCost(diagonal: Boolean, sprint: Boolean): Double {
        val base = if (diagonal) BaritoneActionCosts.WALK_ONE_DIAGONAL_COST else BaritoneActionCosts.WALK_ONE_BLOCK_COST
        return if (sprint) base * BaritoneActionCosts.SPRINT_MULTIPLIER else base
    }

    private fun ascendCost(diagonal: Boolean): Double {
        val base = if (diagonal) {
            BaritoneActionCosts.WALK_ONE_DIAGONAL_COST + BaritoneActionCosts.jumpOneBlockCost
        } else {
            max(BaritoneActionCosts.WALK_ONE_BLOCK_COST, BaritoneActionCosts.jumpOneBlockCost)
        }
        return base + BaritoneActionCosts.PARKOUR_JUMP_PENALTY
    }

    private fun descendCost(fallDistance: Int, diagonal: Boolean): Double {
        val base = if (diagonal) BaritoneActionCosts.WALK_ONE_DIAGONAL_COST else BaritoneActionCosts.WALK_ONE_BLOCK_COST
        return base + when {
            fallDistance == 1 -> BaritoneActionCosts.LANDING_PENALTY
            else -> BaritoneActionCosts.fallCost(fallDistance) + BaritoneActionCosts.CENTER_AFTER_FALL_COST
        }
    }

    private fun parkourCost(distance: Int, ascend: Boolean): Double {
        val base = when (distance) {
            2 -> BaritoneActionCosts.WALK_ONE_BLOCK_COST * 2.0
            3 -> BaritoneActionCosts.WALK_ONE_BLOCK_COST * 3.0
            else -> BaritoneActionCosts.WALK_ONE_BLOCK_COST * 4.0 * BaritoneActionCosts.SPRINT_MULTIPLIER
        }

        return base + BaritoneActionCosts.PARKOUR_JUMP_PENALTY + if (ascend) 0.75 else 0.0
    }

    private fun canSprintFrom(origin: BlockPos): Boolean {
        val support = world.getBlockState(origin.down()).block
        return support !is BlockSoulSand
    }

    private fun isLoadedColumn(x: Int, z: Int): Boolean {
        return world.isBlockLoaded(BlockPos(x, 64, z))
    }

    private fun isPassable(pos: BlockPos): Boolean {
        if (!isLoadedColumn(pos.x, pos.z) || !isWithinWorld(pos.y)) {
            return false
        }

        val state = world.getBlockState(pos)
        val block = state.block

        if (isHazard(pos)) {
            return false
        }

        if (block.isPassable(world, pos)) {
            return true
        }

        if (block is BlockAir) {
            return true
        }

        return collisionBox(block, pos) == null
    }

    private fun canStandOn(pos: BlockPos): Boolean {
        if (!isLoadedColumn(pos.x, pos.z) || !isWithinWorld(pos.y)) {
            return false
        }

        if (isHazard(pos)) {
            return false
        }

        val state = world.getBlockState(pos)
        val block = state.block
        if (block is BlockLiquid || block is BlockFence || block is BlockWall || block is BlockFenceGate) {
            return false
        }

        val box = collisionBox(block, pos) ?: return false
        return box.maxY - box.minY >= 0.0625
    }

    private fun isHazard(pos: BlockPos): Boolean {
        val block = world.getBlockState(pos).block
        return block is BlockLiquid || block is BlockCactus || block is BlockFire
    }

    private fun collisionBox(block: Block, pos: BlockPos): AxisAlignedBB? {
        return block.getCollisionBoundingBox(world, pos, world.getBlockState(pos))
    }

    private fun isWithinWorld(y: Int): Boolean = y in 0 until world.actualHeight

    private fun isBetterCandidate(candidate: NodeRecord, currentBest: NodeRecord): Boolean {
        if (candidate.heuristic + 1.0E-6 < currentBest.heuristic) {
            return true
        }

        return abs(candidate.heuristic - currentBest.heuristic) <= 1.0E-6 && candidate.cost < currentBest.cost
    }

    private fun buildPath(end: NodeRecord, nodesExplored: Int, complete: Boolean): BaritonePath {
        val positions = ArrayList<BlockPos>()
        val steps = ArrayList<BaritonePathStep>()
        var cursor: NodeRecord? = end

        while (cursor != null) {
            positions += cursor.pos
            val previous = cursor.previous
            val moveType = cursor.previousMoveType
            if (previous != null && moveType != null) {
                steps += BaritonePathStep(previous.pos, cursor.pos, moveType, cursor.previousCost)
            }
            cursor = previous
        }

        positions.reverse()
        steps.reverse()

        return BaritonePath(positions, steps, end.cost, nodesExplored, complete)
    }

    private fun pack(x: Int, y: Int, z: Int): Long {
        return ((x.toLong() and 0x3FFFFFFL) shl 38) or
            ((z.toLong() and 0x3FFFFFFL) shl 12) or
            (y.toLong() and 0xFFFL)
    }

    private companion object {
        private val CARDINAL_DIRECTIONS = listOf(
            PathDirection.NORTH,
            PathDirection.SOUTH,
            PathDirection.EAST,
            PathDirection.WEST,
        )

        private val DIAGONAL_DIRECTIONS = listOf(
            PathDirection.NORTH_EAST,
            PathDirection.NORTH_WEST,
            PathDirection.SOUTH_EAST,
            PathDirection.SOUTH_WEST,
        )
    }
}

data class NavigationSettings(
    val repathIntervalMs: Long = 225L,
    val waypointReachDistance: Double = 0.55,
    val waypointVerticalTolerance: Double = 1.35,
    val maxWaypointDistance: Double = 3.5,
    val fallbackToDirectGoal: Boolean = false,
    val sprint: Boolean = true,
    val lookAheadNodes: Int = 2,
    val movementSpeed: Float = 0.98f,
)

enum class NavigationStatus {
    IDLE,
    RUNNING,
    DIRECT,
    PAUSED,
    SUCCESS,
    FAILED,
}

data class PathFollowCommand(
    val active: Boolean,
    val status: NavigationStatus,
    val lookTarget: Vec3? = null,
    val moveForward: Float = 0f,
    val moveStrafe: Float = 0f,
    val jump: Boolean = false,
    val sprint: Boolean = false,
    val sneak: Boolean = false,
    val currentTarget: BlockPos? = null,
    val usingPath: Boolean = false,
) {
    fun applyToKeybinds(gameSettings: net.minecraft.client.settings.GameSettings) {
        gameSettings.keyBindForward.pressed = active && moveForward > 0.15f
        gameSettings.keyBindBack.pressed = active && moveForward < -0.15f
        gameSettings.keyBindLeft.pressed = active && moveStrafe < -0.15f
        gameSettings.keyBindRight.pressed = active && moveStrafe > 0.15f
        gameSettings.keyBindJump.pressed = active && jump
        gameSettings.keyBindSprint.pressed = active && sprint
        gameSettings.keyBindSneak.pressed = active && sneak
    }

    fun applyToMovementInput(input: net.minecraft.util.MovementInput) {
        input.moveForward = if (active) moveForward else 0f
        input.moveStrafe = if (active) moveStrafe else 0f
        input.jump = active && jump
        input.sneak = active && sneak
    }

    companion object {
        fun idle(status: NavigationStatus = NavigationStatus.IDLE): PathFollowCommand {
            return PathFollowCommand(active = false, status = status)
        }
    }
}

class BaritoneNavigationSession(
    private val searchSettings: PathfinderSettings = PathfinderSettings(),
    private val navigationSettings: NavigationSettings = NavigationSettings(),
) {

    private var goal: BaritoneGoal? = null
    private var exactGoal: Vec3? = null
    private var successRadius = 0.75
    private var currentPath: BaritonePath? = null
    private var currentIndex = 1
    private var lastPlanAt = 0L
    private var lastGoalNode: BlockPos? = null
    private var executionState = PathExecutionState()

    var lastSearchResult: PathSearchResult? = null
        private set

    private data class PathExecutionState(
        var step: BaritonePathStep? = null,
        var target: BlockPos? = null,
        var ticksOnStep: Int = 0,
        var jumpStarted: Boolean = false,
        var lastFeet: BlockPos? = null,
        var stationaryTicks: Int = 0,
    ) {
        fun reset(step: BaritonePathStep?, target: BlockPos?) {
            this.step = step
            this.target = target
            ticksOnStep = 0
            jumpStarted = false
            lastFeet = null
            stationaryTicks = 0
        }
    }

    fun updateGoal(goal: BaritoneGoal, exactGoal: Vec3? = null, successRadius: Double = 0.75) {
        if (this.goal != goal || this.exactGoal != exactGoal || this.successRadius != successRadius) {
            this.goal = goal
            this.exactGoal = exactGoal
            this.successRadius = successRadius
            currentPath = null
            currentIndex = 1
            lastPlanAt = 0L
            lastGoalNode = null
            executionState.reset(null, null)
        }
    }

    fun clear() {
        goal = null
        exactGoal = null
        currentPath = null
        currentIndex = 1
        lastPlanAt = 0L
        lastGoalNode = null
        lastSearchResult = null
        executionState.reset(null, null)
    }

    fun tick(world: World?, player: EntityPlayerSP?): PathFollowCommand {
        val activeGoal = goal ?: return PathFollowCommand.idle()
        if (world !is WorldClient || player == null) {
            return PathFollowCommand.idle(NavigationStatus.FAILED)
        }

        if (shouldSuspendNavigation(player)) {
            currentPath = null
            currentIndex = 1
            executionState.reset(null, null)
            return PathFollowCommand.idle(NavigationStatus.PAUSED)
        }

        val preciseGoal = exactGoal
        if (preciseGoal != null && distanceTo(player, preciseGoal) <= successRadius) {
            clearExecutionOnly()
            return PathFollowCommand.idle(NavigationStatus.SUCCESS)
        }

        val playerFeet = playerFeet(player)
        val now = System.currentTimeMillis()
        val goalNode = preciseGoal?.let(::vecToGoalNode)

        if (shouldRepath(player, playerFeet, goalNode, now)) {
            val result = BaritonePathfinder(world, searchSettings).search(playerFeet, activeGoal)
            lastSearchResult = result
            currentPath = result.path
            currentIndex = if ((currentPath?.positions?.size ?: 0) > 1) 1 else 0
            lastPlanAt = now
            lastGoalNode = goalNode
            executionState.reset(null, null)
        }

        val path = currentPath
        if (path != null) {
            advancePath(player, path)

            if (currentIndex in path.positions.indices) {
                val target = path.positions[currentIndex]
                val step = path.steps.getOrNull(currentIndex - 1)
                return createPathCommand(player, path, target, step)
            }

            if (path.complete) {
                return preciseGoal?.let(::createDirectCommand) ?: PathFollowCommand.idle(NavigationStatus.SUCCESS)
            }
        }

        if (navigationSettings.fallbackToDirectGoal && preciseGoal != null) {
            return createDirectCommand(preciseGoal)
        }

        return PathFollowCommand.idle(NavigationStatus.FAILED)
    }

    private fun clearExecutionOnly() {
        executionState.reset(null, null)
    }

    private fun shouldSuspendNavigation(player: EntityPlayerSP): Boolean {
        if (player.isDead || player.health <= 0f || player.isSpectator) {
            return true
        }

        val screen = net.minecraft.client.Minecraft.getMinecraft().currentScreen
        if (screen is GuiDownloadTerrain || screen is GuiGameOver) {
            return true
        }

        if (player.posY < 0.0) {
            return true
        }

        return false
    }

    private fun shouldRepath(player: EntityPlayerSP, playerFeet: BlockPos, goalNode: BlockPos?, now: Long): Boolean {
        val path = currentPath ?: return lastPlanAt == 0L || now - lastPlanAt >= navigationSettings.repathIntervalMs

        if (goalNode != null && lastGoalNode != null && horizontalDistance(goalNode, lastGoalNode!!) > 1.25) {
            return true
        }

        if (now - lastPlanAt < navigationSettings.repathIntervalMs) {
            return false
        }

        if (player.isCollidedHorizontally) {
            return true
        }

        if (currentIndex !in path.positions.indices) {
            return !path.complete
        }

        val nextTarget = path.positions[currentIndex]
        if (horizontalDistance(playerFeet, nextTarget) > navigationSettings.maxWaypointDistance) {
            return true
        }

        return executionState.stationaryTicks >= 6
    }

    private fun advancePath(player: EntityPlayerSP, path: BaritonePath) {
        while (currentIndex in path.positions.indices && hasReachedWaypoint(player, path.positions[currentIndex])) {
            currentIndex++
            executionState.reset(null, null)
        }
    }

    private fun hasReachedWaypoint(player: EntityPlayerSP, target: BlockPos): Boolean {
        if (playerFeet(player) == target) {
            return true
        }

        val dx = player.posX - (target.x + 0.5)
        val dz = player.posZ - (target.z + 0.5)
        val horizontalDistanceSq = dx * dx + dz * dz
        val yDelta = abs(player.entityBoundingBox.minY - target.y.toDouble())

        return horizontalDistanceSq <= navigationSettings.waypointReachDistance * navigationSettings.waypointReachDistance &&
            yDelta <= navigationSettings.waypointVerticalTolerance
    }

    private fun createPathCommand(
        player: EntityPlayerSP,
        path: BaritonePath,
        target: BlockPos,
        step: BaritonePathStep?,
    ): PathFollowCommand {
        if (executionState.step != step || executionState.target != target) {
            executionState.reset(step, target)
        }

        val state = executionState
        state.ticksOnStep++

        val feet = playerFeet(player)
        state.stationaryTicks = if (state.lastFeet == feet) state.stationaryTicks + 1 else 0
        state.lastFeet = feet

        val lookTarget = movementLookTarget(path, target, step)
        val inputState = createMovementInputState(player, step, lookTarget, state)

        return PathFollowCommand(
            active = true,
            status = NavigationStatus.RUNNING,
            lookTarget = lookTarget,
            moveForward = inputState.moveForward,
            moveStrafe = inputState.moveStrafe,
            jump = inputState.jump,
            sprint = inputState.sprint,
            sneak = inputState.sneak,
            currentTarget = target,
            usingPath = true,
        )
    }

    private fun createDirectCommand(target: Vec3): PathFollowCommand {
        val player = net.minecraft.client.Minecraft.getMinecraft().thePlayer ?: return PathFollowCommand.idle(NavigationStatus.FAILED)
        val desiredYaw = desiredYaw(player, target)
        val inputState = analogDirectionalInput(desiredYaw, player.rotationYaw, navigationSettings.sprint)
        return PathFollowCommand(
            active = true,
            status = NavigationStatus.DIRECT,
            lookTarget = target,
            moveForward = inputState.moveForward,
            moveStrafe = inputState.moveStrafe,
            jump = false,
            sprint = inputState.sprint,
            sneak = false,
            currentTarget = null,
            usingPath = false,
        )
    }

    private data class MovementInputState(
        val moveForward: Float,
        val moveStrafe: Float,
        val jump: Boolean,
        val sprint: Boolean,
        val sneak: Boolean = false,
    )

    private fun movementLookTarget(path: BaritonePath, target: BlockPos, step: BaritonePathStep?): Vec3 {
        val lookAheadIndex = min(currentIndex + navigationSettings.lookAheadNodes, path.positions.lastIndex)
        val lookAhead = path.positions[lookAheadIndex]
        val y = when (step?.type) {
            PathMoveType.ASCEND,
            PathMoveType.DIAGONAL_ASCEND,
            PathMoveType.PARKOUR -> max(target.y, lookAhead.y) + 0.85
            else -> lookAhead.y + 0.5
        }
        return Vec3(lookAhead.x + 0.5, y, lookAhead.z + 0.5)
    }

    private fun createMovementInputState(
        player: EntityPlayerSP,
        step: BaritonePathStep?,
        lookTarget: Vec3,
        state: PathExecutionState,
    ): MovementInputState {
        val desiredYaw = desiredYaw(player, lookTarget)
        val base = analogDirectionalInput(desiredYaw, player.rotationYaw, shouldSprint(step))

        return when (step?.type) {
            PathMoveType.ASCEND,
            PathMoveType.DIAGONAL_ASCEND -> base.copy(jump = shouldJumpAscend(player, step, state))
            PathMoveType.PARKOUR -> base.copy(jump = shouldJumpParkour(player, step, state), sprint = true)
            PathMoveType.DESCEND,
            PathMoveType.DIAGONAL_DESCEND,
            PathMoveType.FALL -> base.copy(sneak = shouldSneakEdge(player, step))
            else -> base
        }
    }

    private fun analogDirectionalInput(desiredYaw: Float, referenceYaw: Float, sprint: Boolean): MovementInputState {
        val yawDelta = MathHelper.wrapAngleTo180_float(desiredYaw - referenceYaw)
        val radians = Math.toRadians(yawDelta.toDouble())
        val forward = cos(radians).toFloat().coerceIn(-1f, 1f)
        val strafe = sin(radians).toFloat().coerceIn(-1f, 1f)
        val length = sqrt(forward * forward + strafe * strafe).coerceAtLeast(1f)
        val normalizedForward = (forward / length) * navigationSettings.movementSpeed
        val normalizedStrafe = (strafe / length) * navigationSettings.movementSpeed

        return MovementInputState(
            moveForward = normalizedForward.coerceIn(-1f, 1f),
            moveStrafe = normalizedStrafe.coerceIn(-1f, 1f),
            jump = false,
            sprint = sprint && normalizedForward > 0.35f,
        )
    }

    private fun shouldSprint(step: BaritonePathStep?): Boolean {
        return navigationSettings.sprint || step?.type in setOf(
            PathMoveType.DIAGONAL,
            PathMoveType.DIAGONAL_ASCEND,
            PathMoveType.PARKOUR,
        )
    }

    private fun shouldJumpAscend(player: EntityPlayerSP, step: BaritonePathStep, state: PathExecutionState): Boolean {
        if (!player.onGround || state.jumpStarted || playerFeet(player) == step.to) {
            return false
        }

        val progress = progressFromStart(player, step)
        val threshold = if (step.type == PathMoveType.DIAGONAL_ASCEND) 0.16 else 0.10
        val shouldJump = progress >= threshold || state.ticksOnStep >= 3
        if (shouldJump) {
            state.jumpStarted = true
        }
        return shouldJump
    }

    private fun shouldJumpParkour(player: EntityPlayerSP, step: BaritonePathStep, state: PathExecutionState): Boolean {
        if (!player.onGround || state.jumpStarted) {
            return false
        }

        val distance = max(abs(step.to.x - step.from.x), abs(step.to.z - step.from.z))
        val progress = progressFromStart(player, step)
        val threshold = when (distance) {
            2 -> 0.34
            3 -> 0.62
            else -> 0.82
        }

        val shouldJump = progress >= threshold || state.ticksOnStep >= 5
        if (shouldJump) {
            state.jumpStarted = true
        }
        return shouldJump
    }

    private fun shouldSneakEdge(player: EntityPlayerSP, step: BaritonePathStep): Boolean {
        return player.onGround && step.to.y < step.from.y && progressFromStart(player, step) < 0.18
    }

    private fun progressFromStart(player: EntityPlayerSP, step: BaritonePathStep): Double {
        val startCenterX = step.from.x + 0.5
        val startCenterZ = step.from.z + 0.5
        val deltaX = step.to.x - step.from.x
        val deltaZ = step.to.z - step.from.z

        val progressX = if (deltaX > 0) player.posX - startCenterX else startCenterX - player.posX
        val progressZ = if (deltaZ > 0) player.posZ - startCenterZ else startCenterZ - player.posZ
        return max(progressX, progressZ).coerceAtLeast(0.0)
    }

    private fun desiredYaw(player: EntityPlayerSP, target: Vec3): Float {
        val dx = target.xCoord - player.posX
        val dz = target.zCoord - player.posZ
        return MathHelper.wrapAngleTo180_float(Math.toDegrees(atan2(dz, dx)).toFloat() - 90f)
    }

    private fun distanceTo(player: EntityPlayerSP, target: Vec3): Double {
        val dx = player.posX - target.xCoord
        val dy = player.posY - target.yCoord
        val dz = player.posZ - target.zCoord
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private fun playerFeet(player: EntityPlayerSP): BlockPos {
        return BlockPos(
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.entityBoundingBox.minY + 0.001),
            MathHelper.floor_double(player.posZ),
        )
    }

    private fun horizontalDistance(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dz * dz)
    }

    private fun vecToGoalNode(vec: Vec3): BlockPos {
        return BlockPos(
            MathHelper.floor_double(vec.xCoord),
            MathHelper.floor_double(vec.yCoord),
            MathHelper.floor_double(vec.zCoord),
        )
    }
}
