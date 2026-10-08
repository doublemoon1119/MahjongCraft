package com.doublemoon1119.mahjongcraft.platform.fabric.entity

/**
 * [WinSettlementPresentationEntity] 排名欄位的字串編碼，同步給 client 並隨世界存檔保存。
 *
 * 抽成獨立物件是為了可測試性：實體化 entity 需要註冊表裡的 `EntityType` 與 `World`，本專案沒有
 * Minecraft bootstrap 的測試基礎建設。
 *
 * 每列欄位依序為：玩家 ID、座位、是否 AI、前後分數、前後名次、付款原因 ID（沒有時為空字串）。
 * 欄位數或數字格式不符的列直接略過。
 */
internal object WinSettlementRankingCodec {
    /** 欄位分隔字元。 */
    const val FIELD_SEPARATOR = '\u001f'

    /** 列分隔字元。 */
    const val ROW_SEPARATOR = '\u001e'

    /** 目前格式的欄位數。 */
    private const val FIELD_COUNT = 8

    /** 把排名編碼成單一字串。 */
    fun encode(values: List<WinSettlementRankingSnapshot>): String = values.joinToString(ROW_SEPARATOR.toString()) {
        listOf(
            it.playerId,
            it.seatIndex,
            if (it.isAi) 1 else 0,
            it.previousScore,
            it.currentScore,
            it.previousRank,
            it.currentRank,
            it.paymentReasonId.orEmpty(),
        ).joinToString(FIELD_SEPARATOR.toString())
    }

    /** 由 [encode] 的結果讀回排名。 */
    fun decode(encoded: String): List<WinSettlementRankingSnapshot> = encoded.split(ROW_SEPARATOR).mapNotNull { row ->
        val fields = row.split(FIELD_SEPARATOR)
        if (fields.size != FIELD_COUNT) return@mapNotNull null
        WinSettlementRankingSnapshot(
            playerId = fields[0],
            seatIndex = fields[1].toIntOrNull() ?: return@mapNotNull null,
            isAi = fields[2] == "1",
            previousScore = fields[3].toIntOrNull() ?: return@mapNotNull null,
            currentScore = fields[4].toIntOrNull() ?: return@mapNotNull null,
            previousRank = fields[5].toIntOrNull() ?: return@mapNotNull null,
            currentRank = fields[6].toIntOrNull() ?: return@mapNotNull null,
            paymentReasonId = fields[7].ifBlank { null },
        )
    }
}
