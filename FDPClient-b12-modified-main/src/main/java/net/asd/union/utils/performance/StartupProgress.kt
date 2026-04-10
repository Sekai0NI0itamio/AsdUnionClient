package net.asd.union.utils.performance

import java.util.concurrent.atomic.AtomicReference

object StartupProgress {
    const val STEP_INITIALIZE = 0
    const val STEP_PRELOAD = 1
    const val STEP_STARTUP = 2
    const val STEP_FONTS = 3
    const val STEP_MODULES = 4
    const val STEP_FINALIZE = 5

    private val stepLabels = listOf(
        "Initialize fast startup",
        "Optimized preload tasks",
        "Optimized startup",
        "Load fonts",
        "Load modules",
        "Finalize startup",
    )

    private val statuses = Array(stepLabels.size) { Status.PENDING }

    @Volatile
    private var active = false

    @Volatile
    private var currentIndex = 0

    @Volatile
    private var subProgress = 0f

    private val snapshotRef = AtomicReference(buildSnapshot())

    @Synchronized
    fun start() {
        active = true
        currentIndex = 0
        subProgress = 0f
        statuses.fill(Status.PENDING)
        statuses[0] = Status.ACTIVE
        updateSnapshot()
    }

    @Synchronized
    fun advanceTo(stepIndex: Int) {
        if (stepIndex !in stepLabels.indices) {
            return
        }

        if (!active) {
            start()
        }

        for (i in 0 until stepIndex) {
            statuses[i] = Status.COMPLETE
        }

        statuses[stepIndex] = Status.ACTIVE

        for (i in stepIndex + 1 until statuses.size) {
            if (statuses[i] == Status.COMPLETE) {
                statuses[i] = Status.PENDING
            }
        }

        currentIndex = stepIndex
        subProgress = 0f
        updateSnapshot()
    }

    @Synchronized
    fun complete() {
        if (!active) {
            return
        }

        statuses.fill(Status.COMPLETE)
        currentIndex = statuses.lastIndex
        subProgress = 1f
        active = false
        updateSnapshot()
    }

    @Synchronized
    fun updateSubProgress(value: Float) {
        subProgress = value.coerceIn(0f, 1f)
        updateSnapshot()
    }

    fun snapshot(): Snapshot = snapshotRef.get()

    fun isActive(): Boolean = snapshotRef.get().active

    private fun updateSnapshot() {
        snapshotRef.set(buildSnapshot())
    }

    private fun buildSnapshot(): Snapshot {
        val stepsSnapshot = stepLabels.mapIndexed { index, label ->
            StepSnapshot(label, statuses[index])
        }

        return Snapshot(
            active = active,
            steps = stepsSnapshot,
            currentIndex = currentIndex.coerceIn(0, stepLabels.lastIndex),
            completed = statuses.count { it == Status.COMPLETE },
            total = stepLabels.size,
            subProgress = subProgress,
        )
    }

    enum class Status {
        PENDING,
        ACTIVE,
        COMPLETE,
    }

    data class StepSnapshot(
        val label: String,
        val status: Status,
    )

    data class Snapshot(
        val active: Boolean,
        val steps: List<StepSnapshot>,
        val currentIndex: Int,
        val completed: Int,
        val total: Int,
        val subProgress: Float,
    ) {
        val percent: Int
            get() = if (total == 0) 0 else (((completed.toFloat() + subProgress) / total.toFloat()) * 100f).toInt()

        val remaining: Int
            get() = (total - completed).coerceAtLeast(0)

        val currentLabel: String
            get() = steps.getOrNull(currentIndex)?.label ?: "Starting..."
    }
}
