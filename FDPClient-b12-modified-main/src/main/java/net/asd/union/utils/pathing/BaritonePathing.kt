package net.asd.union.utils.pathing

import net.minecraft.block.Block
import net.minecraft.block.BlockCactus
import net.minecraft.block.BlockFence
import net.minecraft.block.BlockFenceGate
import net.minecraft.block.BlockFire
import net.minecraft.block.BlockLiquid
import net.minecraft.block.BlockSoulSand
import net.minecraft.block.BlockWall
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.MathHelper
import net.minecraft.util.Vec3
import net.minecraft.world.World
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.sqrt

object BaritoneActionCosts {
    const val WALK_ONE_BLOCK_COST = 20.0 / 4.317
    const val WALK_OFF_BLOCK_COST = WALK_ONE_BLOCK_COST * 0.8
    const val CENTER_AFTER_FALL_COST = WALK_ONE_BLOCK_COST - WALK_OFF_BLOCK_COST
    const val COST_INF = 1_000_000.0

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
            return (diagonal + straight) * BaritoneActionCosts.WALK_ONE_BLOCK_COST
        }
    }
}

data class BaritoneGoalYLevel(val level: Int) : BaritoneGoal {
    override fun isInGoal(x: Int, y: Int, z: Int): Boolean = y == level

    override fun heuristic(x: Int, y: Int, z: Int): Double = calculate(level, y)

    companion object {
        fun calculate(goalY: Int, currentY: Int): Double {
            return when {
                currentY > goalY -> BaritoneActionCosts.fallCost(2) / 2.0 * (currentY - goalY)
                currentY < goalY -> (goalY - currentY) * BaritoneActionCosts.jumpOneBlockCost
                else -> 0.0
            }
        }
    }
}

enum class PathMoveType {
    WALK,
    ASCEND,
    DESCEND,
    FALL,
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
    val maxNodes: Int = 4_096,
    val maxFallDistance: Int = 3,
    val startResolveHorizontalRange: Int = 1,
    val startResolveVerticalRange: Int = 2,
)

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
            ?: return PathSearchResult(
                status = PathSearchStatus.FAILED,
                path = null,
                nodesExplored = 0,
                resolvedStart = null,
                reason = "Unable to resolve a valid start node from $start",
            )

        if (goal.isInGoal(resolvedStart)) {
            val immediatePath = BaritonePath(
                positions = listOf(resolvedStart),
                steps = emptyList(),
                totalCost = 0.0,
                nodesExplored = 0,
                complete = true,
            )

            return PathSearchResult(
                status = PathSearchStatus.SUCCESS,
                path = immediatePath,
                nodesExplored = 0,
                resolvedStart = resolvedStart,
                reason = null,
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
        openSet.add(OpenEntry(startNode.combinedCost, sequence++, startNode))

        var bestNode = startNode

        while (openSet.isNotEmpty() && nodesExplored < settings.maxNodes) {
            val entry = openSet.poll()
            val current = entry.node

            if (entry.combinedCost > current.combinedCost || current.closed) {
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

                openSet.add(OpenEntry(neighbor.combinedCost, sequence++, neighbor))
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
        val transitions = ArrayList<Transition>(12)

        transitions += collectTransition(origin, 1, 0)
        transitions += collectTransition(origin, -1, 0)
        transitions += collectTransition(origin, 0, 1)
        transitions += collectTransition(origin, 0, -1)

        return transitions
    }

    private fun collectTransition(origin: BlockPos, dx: Int, dz: Int): List<Transition> {
        val transitions = ArrayList<Transition>(1 + settings.maxFallDistance)
        val targetX = origin.x + dx
        val targetZ = origin.z + dz
        val sameLevel = BlockPos(targetX, origin.y, targetZ)

        if (!isLoadedColumn(targetX, targetZ)) {
            return transitions
        }

        if (isPassable(sameLevel) && isPassable(sameLevel.up()) && canStandOn(sameLevel.down())) {
            transitions.add(
                Transition(
                    destination = sameLevel,
                    cost = BaritoneActionCosts.WALK_ONE_BLOCK_COST,
                    type = PathMoveType.WALK,
                )
            )
        }

        val ascend = BlockPos(targetX, origin.y + 1, targetZ)
        if (isPassable(ascend) && isPassable(ascend.up()) && canStandOn(ascend.down()) && isPassable(origin.up(2))) {
            transitions.add(
                Transition(
                    destination = ascend,
                    cost = BaritoneActionCosts.jumpOneBlockCost,
                    type = PathMoveType.ASCEND,
                )
            )
        }

        for (fallDistance in 1..settings.maxFallDistance) {
            val descend = BlockPos(targetX, origin.y - fallDistance, targetZ)
            if (descend.y < 1 || !canStandOn(descend.down())) {
                continue
            }

            var clearColumn = true

            for (openY in descend.y..origin.y) {
                val columnPos = BlockPos(targetX, openY, targetZ)
                if (!isPassable(columnPos)) {
                    clearColumn = false
                    break
                }
            }

            if (!clearColumn || !isPassable(descend.up())) {
                continue
            }

            transitions.add(
                Transition(
                    destination = descend,
                    cost = if (fallDistance == 1) {
                        BaritoneActionCosts.WALK_OFF_BLOCK_COST + BaritoneActionCosts.CENTER_AFTER_FALL_COST
                    } else {
                        BaritoneActionCosts.WALK_OFF_BLOCK_COST +
                            BaritoneActionCosts.fallCost(fallDistance) +
                            BaritoneActionCosts.CENTER_AFTER_FALL_COST
                    },
                    type = if (fallDistance == 1) PathMoveType.DESCEND else PathMoveType.FALL,
                )
            )
        }

        return transitions
    }

    private fun buildPath(end: NodeRecord, nodesExplored: Int, complete: Boolean): BaritonePath {
        val positions = ArrayList<BlockPos>()
        val steps = ArrayList<BaritonePathStep>()

        var cursor: NodeRecord? = end

        while (cursor != null) {
            positions.add(cursor.pos)

            val previous = cursor.previous
            val previousMoveType = cursor.previousMoveType

            if (previous != null && previousMoveType != null) {
                steps.add(
                    BaritonePathStep(
                        from = previous.pos,
                        to = cursor.pos,
                        type = previousMoveType,
                        cost = cursor.previousCost,
                    )
                )
            }

            cursor = previous
        }

        positions.reverse()
        steps.reverse()

        return BaritonePath(
            positions = positions,
            steps = steps,
            totalCost = end.cost,
            nodesExplored = nodesExplored,
            complete = complete,
        )
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

        if (block is BlockLiquid) {
            return false
        }

        if (block is BlockCactus || block is BlockFire) {
            return false
        }

        if (block.isPassable(world, pos)) {
            return true
        }

        return collisionBox(block, pos) == null
    }

    private fun canStandOn(pos: BlockPos): Boolean {
        if (!isLoadedColumn(pos.x, pos.z) || !isWithinWorld(pos.y)) {
            return false
        }

        val state = world.getBlockState(pos)
        val block = state.block

        if (block is BlockLiquid || block is BlockFence || block is BlockWall || block is BlockFenceGate) {
            return false
        }

        if (block is BlockCactus || block is BlockFire) {
            return false
        }

        if (block is BlockSoulSand) {
            return true
        }

        val box = collisionBox(block, pos) ?: return false
        val standingHeight = box.maxY - box.minY
        return standingHeight >= 0.9
    }

    private fun collisionBox(block: Block, pos: BlockPos): AxisAlignedBB? {
        return block.getCollisionBoundingBox(world, pos, world.getBlockState(pos))
    }

    private fun isWithinWorld(y: Int): Boolean {
        return y in 0 until world.actualHeight
    }

    private fun isBetterCandidate(candidate: NodeRecord, currentBest: NodeRecord): Boolean {
        if (candidate.heuristic + 1.0E-6 < currentBest.heuristic) {
            return true
        }

        if (abs(candidate.heuristic - currentBest.heuristic) <= 1.0E-6) {
            return candidate.cost < currentBest.cost
        }

        return false
    }

    private fun pack(x: Int, y: Int, z: Int): Long {
        return ((x.toLong() and 0x3FFFFFFL) shl 38) or
            ((z.toLong() and 0x3FFFFFFL) shl 12) or
            (y.toLong() and 0xFFFL)
    }
}

data class NavigationSettings(
    val repathIntervalMs: Long = 350L,
    val waypointReachDistance: Double = 0.5,
    val waypointVerticalTolerance: Double = 1.25,
    val maxWaypointDistance: Double = 2.75,
    val fallbackToDirectGoal: Boolean = true,
    val sprint: Boolean = false,
)

enum class NavigationStatus {
    IDLE,
    RUNNING,
    DIRECT,
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
    val currentTarget: BlockPos? = null,
    val usingPath: Boolean = false,
) {
    fun applyToKeybinds(gameSettings: net.minecraft.client.settings.GameSettings) {
        gameSettings.keyBindForward.pressed = active && moveForward > 0f
        gameSettings.keyBindBack.pressed = active && moveForward < 0f
        gameSettings.keyBindLeft.pressed = active && moveStrafe < 0f
        gameSettings.keyBindRight.pressed = active && moveStrafe > 0f
        gameSettings.keyBindJump.pressed = active && jump
        gameSettings.keyBindSprint.pressed = active && sprint
    }

    fun applyToMovementInput(input: net.minecraft.util.MovementInput) {
        input.moveForward = if (active) moveForward else 0f
        input.moveStrafe = if (active) moveStrafe else 0f
        input.jump = active && jump
        input.sneak = false
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

    var lastSearchResult: PathSearchResult? = null
        private set

    fun updateGoal(goal: BaritoneGoal, exactGoal: Vec3? = null, successRadius: Double = 0.75) {
        if (this.goal != goal || this.exactGoal != exactGoal || this.successRadius != successRadius) {
            this.goal = goal
            this.exactGoal = exactGoal
            this.successRadius = successRadius
            currentPath = null
            currentIndex = 1
            lastPlanAt = 0L
        }
    }

    fun clear() {
        goal = null
        exactGoal = null
        currentPath = null
        currentIndex = 1
        lastPlanAt = 0L
        lastSearchResult = null
    }

    fun tick(world: World?, player: EntityPlayerSP?): PathFollowCommand {
        val activeGoal = goal ?: return PathFollowCommand.idle()
        if (world == null || player == null) {
            return PathFollowCommand.idle(NavigationStatus.FAILED)
        }

        val preciseGoal = exactGoal
        if (preciseGoal != null && horizontalDistance(player, preciseGoal) <= successRadius) {
            return PathFollowCommand.idle(NavigationStatus.SUCCESS)
        }

        val playerFeet = playerFeet(player)
        val now = System.currentTimeMillis()

        if (shouldRepath(player, playerFeet, now)) {
            val result = BaritonePathfinder(world, searchSettings).search(playerFeet, activeGoal)
            lastSearchResult = result
            currentPath = result.path
            currentIndex = if ((currentPath?.positions?.size ?: 0) > 1) 1 else 0
            lastPlanAt = now
        }

        val path = currentPath
        if (path != null) {
            advancePath(player, path)

            if (currentIndex in path.positions.indices) {
                val target = path.positions[currentIndex]
                val step = path.steps.getOrNull(currentIndex - 1)
                return createPathCommand(player, target, step)
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

    private fun shouldRepath(player: EntityPlayerSP, playerFeet: BlockPos, now: Long): Boolean {
        val path = currentPath ?: return lastPlanAt == 0L || now - lastPlanAt >= navigationSettings.repathIntervalMs
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
        return horizontalDistance(playerFeet, nextTarget) > navigationSettings.maxWaypointDistance
    }

    private fun advancePath(player: EntityPlayerSP, path: BaritonePath) {
        while (currentIndex in path.positions.indices && hasReachedWaypoint(player, path.positions[currentIndex])) {
            currentIndex++
        }
    }

    private fun hasReachedWaypoint(player: EntityPlayerSP, target: BlockPos): Boolean {
        if (playerFeet(player) == target) {
            return true
        }

        val dx = player.posX - (target.x + 0.5)
        val dz = player.posZ - (target.z + 0.5)
        val horizontalDistanceSq = dx * dx + dz * dz
        val yDelta = abs(player.posY - target.y.toDouble())

        return horizontalDistanceSq <= navigationSettings.waypointReachDistance * navigationSettings.waypointReachDistance &&
            yDelta <= navigationSettings.waypointVerticalTolerance
    }

    private fun createPathCommand(player: EntityPlayerSP, target: BlockPos, step: BaritonePathStep?): PathFollowCommand {
        val lookTarget = Vec3(target.x + 0.5, target.y + 0.5, target.z + 0.5)
        val shouldJump = step?.type == PathMoveType.ASCEND && player.onGround

        return PathFollowCommand(
            active = true,
            status = NavigationStatus.RUNNING,
            lookTarget = lookTarget,
            moveForward = 1f,
            moveStrafe = 0f,
            jump = shouldJump,
            sprint = navigationSettings.sprint,
            currentTarget = target,
            usingPath = true,
        )
    }

    private fun createDirectCommand(target: Vec3): PathFollowCommand {
        return PathFollowCommand(
            active = true,
            status = NavigationStatus.DIRECT,
            lookTarget = target,
            moveForward = 1f,
            moveStrafe = 0f,
            jump = false,
            sprint = navigationSettings.sprint,
            currentTarget = null,
            usingPath = false,
        )
    }

    private fun playerFeet(player: EntityPlayerSP): BlockPos {
        return BlockPos(
            MathHelper.floor_double(player.posX),
            MathHelper.floor_double(player.entityBoundingBox.minY + 0.001),
            MathHelper.floor_double(player.posZ),
        )
    }

    private fun horizontalDistance(player: EntityPlayerSP, target: Vec3): Double {
        val dx = player.posX - target.xCoord
        val dz = player.posZ - target.zCoord
        return sqrt(dx * dx + dz * dz)
    }

    private fun horizontalDistance(a: BlockPos, b: BlockPos): Double {
        val dx = (a.x - b.x).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dz * dz)
    }
}
