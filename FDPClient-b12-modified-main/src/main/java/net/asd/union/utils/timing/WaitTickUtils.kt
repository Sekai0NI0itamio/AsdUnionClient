/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge by LiquidBounce.
 * https://github.com/Itamio/FDPClient/
 */
package net.asd.union.utils.timing

import net.asd.union.event.GameTickEvent
import net.asd.union.event.Listenable
import net.asd.union.event.handler
import net.asd.union.utils.client.ClientUtils
import net.asd.union.utils.client.MinecraftInstance
import net.asd.union.utils.kotlin.removeEach

object WaitTickUtils : MinecraftInstance, Listenable {

    private val scheduledActions = ArrayDeque<ScheduledAction>()

    fun schedule(ticks: Int, requester: Any? = null, action: () -> Unit = { }) =
        conditionalSchedule(requester, ticks, false) { action(); null }

    fun conditionalSchedule(
        requester: Any? = null,
        ticks: Int? = null,
        isConditional: Boolean = true,
        action: (Int) -> Boolean?
    ) {
        if (ticks == 0) {
            action(0)

            return
        }

        val time = ticks ?: 0

        scheduledActions += ScheduledAction(requester, time, isConditional, ClientUtils.runTimeTicks + time, action)
    }

    fun hasScheduled(obj: Any) = scheduledActions.firstOrNull { it.requester == obj } != null

    val onTick = handler<GameTickEvent>(priority = -1) {
        val currentTick = ClientUtils.runTimeTicks

        scheduledActions.removeEach { action ->
            val elapsed = action.duration - (action.ticks - currentTick)
            val shouldRemove = currentTick >= action.ticks

            return@removeEach when {
                !action.isConditional -> {
                    { action.action(elapsed) ?: true }.takeIf { shouldRemove }?.invoke() ?: false
                }
                else -> action.action(elapsed) ?: shouldRemove
            }
        }
    }

    private data class ScheduledAction(
        val requester: Any?,
        val duration: Int,
        val isConditional: Boolean,
        val ticks: Int,
        val action: (Int) -> Boolean?
    )

}