package com.doublemoon1119.mahjongcraft.logic.table.opening

/**
 * 由規則決定本局如何透過一輪或多輪擲骰選定起家與開門位置的純計算程序。
 *
 * 實作應只根據輸入情境決定下一步，不應依賴或修改外部可變狀態；同一規則設定與情境必須得到
 * 相同結果。空集合代表此規則採用不需擲骰的固定開局程序。
 */
interface RoundOpeningProcedure {
    /** 此程序可能要求的骰子數量；空集合表示程序不會要求擲骰。 */
    val supportedDiceCounts: Set<Int>

    /**
     * 依已完成的擲骰記錄決定下一步或完成結果。
     *
     * @param context 目前開局情境及已完成的擲骰記錄；實作不得修改其中的集合。
     * @return 下一次擲骰要求，或已完成的起家與開門結果。
     */
    fun next(context: RoundOpeningContext): RoundOpeningStep
}
