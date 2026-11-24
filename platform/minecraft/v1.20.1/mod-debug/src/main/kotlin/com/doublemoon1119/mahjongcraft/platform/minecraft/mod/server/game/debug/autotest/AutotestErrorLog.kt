package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.AbstractAppender
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 記下本 mod 在自動測試期間寫出的錯誤紀錄，讓報告檢查伺服器沒有記錄任何錯誤。
 *
 * 只收集名稱以本 mod 開頭的 logger 寫出的 ERROR 以上的紀錄；紀錄照常寫進伺服器的 log。
 */
internal object AutotestErrorLog {
    /** 收集到的錯誤，依發生順序，每筆是 logger 名稱與訊息。 */
    private val errors = CopyOnWriteArrayList<String>()

    /** 是否已經掛上收集器。 */
    private var installed = false

    /** 掛上收集器；只掛一次。 */
    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        val context = LogManager.getContext(false) as LoggerContext

        // Forge 1.20.1 附帶的 log4j 較舊，只有這個建構子兩個載入器都有。
        @Suppress("DEPRECATION")
        val appender = object : AbstractAppender(APPENDER_NAME, null, null, true) {
            override fun append(event: LogEvent) {
                if (event.level.isMoreSpecificThan(Level.ERROR) && event.loggerName.startsWith(LOGGER_PREFIX)) {
                    errors += "${event.loggerName}: ${event.message.formattedMessage}"
                }
            }
        }
        appender.start()
        context.configuration.rootLogger.addAppender(appender, Level.ERROR, null)
        context.updateLoggers()
    }

    /**
     * 取出從上次取出後收集到的錯誤。
     *
     * @return 依發生順序的錯誤。
     */
    fun drain(): List<String> = buildList {
        while (errors.isNotEmpty()) add(errors.removeAt(0))
    }

    /** 收集器在 log 設定中的名稱。 */
    private const val APPENDER_NAME = "MahjongCraftAutotestErrors"

    /** 本 mod 的 logger 名稱開頭。 */
    private val LOGGER_PREFIX = "${MinecraftModMetadata.MOD_NAME}/"
}
