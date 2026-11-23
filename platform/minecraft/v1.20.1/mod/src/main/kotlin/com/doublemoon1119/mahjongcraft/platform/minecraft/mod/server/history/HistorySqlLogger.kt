package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.expandArgs

/**
 * 以 trace 等級記錄歷史資料庫執行的每一句 SQL。
 *
 * 背景寫入與查詢會持續執行 SQL，逐句記錄只在追查資料庫問題時有用，因此不使用 Exposed 預設的 debug 等級。
 */
internal object HistorySqlLogger : SqlLogger {
    /** 記錄 SQL 的記錄器。 */
    private val logger = mahjongCraftLogger(HistorySqlLogger::class)

    override fun log(context: StatementContext, transaction: Transaction) {
        if (logger.isTraceEnabled) logger.trace(context.expandArgs(transaction))
    }
}
