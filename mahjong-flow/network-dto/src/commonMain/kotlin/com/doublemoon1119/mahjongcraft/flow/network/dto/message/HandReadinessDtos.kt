package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import kotlinx.serialization.Serializable

/**
 * 一張等待牌及依玩家可見資訊推算的剩餘張數。
 *
 * [winAvailability] 是規則模組自訂的命名字串，比照 [DiscardReadinessAnalysisDto.statusIndicatorId] 的
 * 慣例：預設值 [WIN_AVAILABLE_ID] 代表「沒有任何和牌資格上的特殊限制」，這是所有規則模組共通的中立
 * 預設；有更細分和牌可用性概念的規則模組（例如日麻的自摸限定、無役、未達最低翻符）另外提供各自的
 * 命名字串，client 端依 namespaced ID 映射顯示文字，查不到時安全 fallback 顯示原始字串（同
 * [DiscardReadinessAnalysisDto.statusIndicatorId] 與局況／公開玩家指示的
 * 既有慣例）。
 *
 * @property tile 等待的牌種；顯示用的牌面由平台依牌種決定。
 * @property remainingCount 依玩家可見資訊推算的剩餘張數。
 * @property winAvailability 這張牌的和牌資格。
 */
@Serializable
data class WaitingTileAvailabilityDto(
    val tile: TileDto,
    val remainingCount: Int,
    val winAvailability: String = WIN_AVAILABLE_ID,
)

/** [WaitingTileAvailabilityDto.winAvailability] 的中立預設值，代表這張等待牌沒有和牌資格上的特殊限制。 */
const val WIN_AVAILABLE_ID = "mahjongcraft:win_available"

/** 打出指定實體手牌後的聽牌分析。 */
@Serializable
data class DiscardReadinessAnalysisDto(
    val discardTileId: String,
    val waitingTiles: List<WaitingTileAvailabilityDto>,
    val statusIndicatorId: String? = null,
)

/** 玩家目前手牌的等待牌與整體狀態，不包含任何假想捨牌。 */
@Serializable
data class HandReadinessAnalysisDto(
    val ruleModuleId: String,
    val waitingTiles: List<WaitingTileAvailabilityDto>,
    val statusIndicatorId: String? = null,
)
