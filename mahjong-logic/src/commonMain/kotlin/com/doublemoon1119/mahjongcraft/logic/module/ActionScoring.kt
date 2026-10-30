package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import kotlin.uuid.Uuid

/**
 * 一次動作成立當下的點數收付。
 *
 * 點數只在玩家與規則自己的點數池之間移動：[scoreDeltas] 的合計加上 [potDeposit] 必須為 0。點數池的餘額由規則存在
 * 桌況組件，跨局保留與和牌時分配都由規則負責，例如日麻的立直供託。Flow 會驗證玩家與合計，
 * 回傳物件不代表已經寫入。
 *
 * @property scoreDeltas 各玩家的點數增減；只能列出本局玩家，不可為空，也不可包含 0。
 * @property potDeposit 放進規則點數池的點數，正數為放入、負數為取出；沒有點數池時為 0。
 * @property tableComponents 本次明確設定或移除的桌況組件；未指定的組件保持原值。
 */
data class ActionScoring(
    val scoreDeltas: Map<Uuid, Int>,
    val potDeposit: Int,
    val tableComponents: ComponentPatch,
)
