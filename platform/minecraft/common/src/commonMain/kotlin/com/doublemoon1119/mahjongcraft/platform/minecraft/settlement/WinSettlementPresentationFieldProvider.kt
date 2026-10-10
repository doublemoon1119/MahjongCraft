package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 欄位 provider 只能讀取不可變快照，不能接觸或修改權威桌況。 */
fun interface WinSettlementPresentationFieldProvider {
    /** 依快照產生欄位值；沒有值時回傳 null。 */
    fun provide(snapshot: WinSettlementPresentationFieldSnapshot): PresentationValue?
}
