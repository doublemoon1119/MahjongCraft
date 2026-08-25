package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import kotlin.uuid.Uuid

/** 內建回合結算玩家狀態 ID。 */
object BuiltInRoundSettlementStatusIds {
    /** 一般荒牌流局的聽牌者。 */
    val TENPAI: String = MahjongCraftMetadata.id("tenpai")

    /** 一般荒牌流局的未聽者。 */
    val NOTEN: String = MahjongCraftMetadata.id("noten")

    /** 需要公開手牌證明流局宣告成立，但不代表聽牌。 */
    val DRAW_DECLARATION: String = MahjongCraftMetadata.id("draw_declaration")
}

/** 流局結算時一副手牌應採用的公開動畫策略。 */
enum class RoundSettlementHandPresentation {
    /** 公開聽牌手牌並顯示規則提供的等待牌。 */
    REVEAL_TENPAI,

    /** 公開宣告手牌作為成立證明，但不顯示等待牌。 */
    REVEAL_PROOF,

    /** 不公開手牌，將立牌平滑蓋成牌背朝上。 */
    CONCEAL,
}

/**
 * 單一玩家的回合結算呈現關鍵影格。
 *
 * @property playerId 玩家 Uuid。
 * @property seatIndex 固定座位 index。
 * @property currentWind 結算當下風位。
 * @property isAi 是否由 AI 操控。
 * @property previousScore 結算前總分。
 * @property currentScore 結算後總分。
 * @property previousRank 結算前名次，從 1 開始。
 * @property currentRank 結算後名次，從 1 開始。
 * @property handTileIds 這名玩家完整手牌的 Uuid，僅供結算動畫定位，不包含副露或牌河。
 * @property handPresentation 這副手牌在結算時採用的公開動畫策略。
 * @property revealedHandTileIds 規則要求公開的完整手牌 Uuid；空集合代表不推牌。
 * @property waitingTiles 規則已計算完成的等待牌；空集合代表不顯示等待牌。
 * @property statusId 玩家狀態的 namespaced ID；不需要額外狀態時為 null。
 */
data class RoundSettlementPlayerPresentation(
    val playerId: Uuid,
    val seatIndex: Int,
    val currentWind: Wind,
    val isAi: Boolean,
    val previousScore: Int,
    val currentScore: Int,
    val previousRank: Int,
    val currentRank: Int,
    val handTileIds: List<Uuid>,
    val handPresentation: RoundSettlementHandPresentation,
    val revealedHandTileIds: List<Uuid>,
    val waitingTiles: List<Tile>,
    val statusId: String?,
)

/**
 * 平台無關的統一流局結算呈現請求。
 *
 * @property reasonId 規則提供的完整流局原因 ID。
 * @property players 依固定座位順序排列的玩家結算資料。
 */
data class RoundSettlementPresentationRequest(
    val reasonId: String,
    val players: List<RoundSettlementPlayerPresentation>,
)
