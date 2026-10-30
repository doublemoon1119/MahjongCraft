package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 桌面物件幾何配置結果。
 */
sealed interface TablePropLayoutResult {
    /**
     * 配置成功。
     *
     * @property targets 已完成驗證與配置的世界目標。
     */
    data class Success(val targets: List<TablePropTarget>) : TablePropLayoutResult

    /**
     * 配置失敗，平台不得套用部分結果。
     *
     * @property reason 失敗原因。
     * @property diagnostic 只供記錄使用的診斷文字。
     */
    data class Invalid(
        val reason: TablePropLayoutFailure,
        val diagnostic: String,
    ) : TablePropLayoutResult
}
