package net.asd.union.handler.sessiontabs

import net.asd.union.file.FileManager
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.filter.AbstractFilter
import org.apache.logging.log4j.core.layout.PatternLayout
import java.io.File
import java.io.Serializable

object TabLogRouter {

    private const val APPENDER_NAME = "AsdUnionDetachedTabFileAppender"

    @Volatile
    private var installed = false

    fun install() {
        if (installed) {
            return
        }

        val loggerContext = LogManager.getContext(false) as? LoggerContext ?: return

        synchronized(this) {
            if (installed) {
                return
            }

            runCatching {
                val configuration = loggerContext.configuration ?: return
                val rootLogger = configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME)

                val layout = PatternLayout.createLayout(
                    "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t/%level] %logger - %msg%n%throwable",
                    configuration,
                    null,
                    null,
                    null
                )

                val appender = DetachedTabFileAppender(APPENDER_NAME, layout)
                appender.start()

                rootLogger.addAppender(appender, null, null)

                rootLogger.appenders.values.forEach { existingAppender: org.apache.logging.log4j.core.Appender ->
                    if (existingAppender is ConsoleAppender) {
                        existingAppender.addFilter(DetachedTabConsoleFilter)
                    }
                }

                loggerContext.updateLoggers()
                installed = true
            }
        }
    }

    private object DetachedTabConsoleFilter : AbstractFilter() {
        override fun filter(event: LogEvent?): Filter.Result {
            return if (SessionRuntimeScope.isDetachedContextActive() ||
                Thread.currentThread() is TabSimulationThread) {
                Filter.Result.DENY
            } else {
                Filter.Result.NEUTRAL
            }
        }
    }

    private class DetachedTabFileAppender(
        name: String,
        layout: Layout<out Serializable>
    ) : AbstractAppender(name, null, layout, true) {

        override fun append(event: LogEvent) {
            val runtime = SessionRuntimeScope.currentRuntime()
                ?: (Thread.currentThread() as? TabSimulationThread)?.runtime
                ?: return
            val serialized = layout.toSerializable(event).toString()
            val targetFile = resolveLogFile(runtime)

            synchronized(this) {
                targetFile.parentFile?.mkdirs()
                targetFile.appendText(serialized, Charsets.UTF_8)
            }
        }

        private fun resolveLogFile(runtime: LiveTabRuntime): File {
            val logsDir = File(FileManager.dir, "Logs")
            val safeName = buildString {
                val source = runtime.debugName.ifBlank { runtime.tabId }
                source.forEach { character ->
                    append(
                        when {
                            character.isLetterOrDigit() || character == '-' || character == '_' -> character
                            else -> '_'
                        }
                    )
                }
            }.ifBlank { "tab" }

            return File(logsDir, "${safeName}_${runtime.tabId}.log")
        }
    }
}
