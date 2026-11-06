package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 將權威胡牌結果轉換為規則專屬的語意詳情欄位，不指定其呈現方式。
 *
 * 詳情是可選的附加資料；沒有對應解析器或解析器不支援該結果時，呼叫端會使用空清單，
 * 不影響分數、贏家、名次或其他共用結算資料。
 */
interface WinSettlementDetailResolver {
    /**
     * 解析一般胡牌（自摸／榮和）的詳情；[handValue] 一律是實際成立的胡牌結果。
     *
     * 牌面欄位一律以 [publicFace] 轉換，只帶公共讀取者可知的外觀與特徵，不引用局內牌張。
     *
     * @param state 胡牌成立時的權威桌況。
     * @param winnerId 這筆詳情所屬的贏家。
     * @param responsiblePlayerId 單獨付款的玩家，例如放銃者；由其他玩家一起付款（例如自摸）時為 null。
     * @param handValue 已完成計算的胡牌結果。
     * @param publicFace 將牌值轉成公開牌面的投影。
     * @return 規則專屬的胡牌詳情欄位；沒有要提供附加資料時回傳空清單。
     */
    fun resolve(
        state: TableState,
        winnerId: Uuid,
        responsiblePlayerId: Uuid?,
        handValue: HandValueResult,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField>

    /**
     * 解析不含 [HandValueResult] 的特殊胡牌等價結果，例如流局滿貫。
     *
     * @param state 特殊結果成立時的權威桌況。
     * @param outcome 欲解析的特殊回合結果。
     * @param publicFace 將牌值轉成公開牌面的投影。
     * @return 規則專屬詳情欄位；不認得或不支援結果時為 `null`，由 registry 轉為空清單。
     */
    fun resolveSpecialOutcome(
        state: TableState,
        outcome: ResolvedRoundOutcome,
        publicFace: (TileValue) -> TileFaceView,
    ): List<WinSettlementDetailField>? = null
}
