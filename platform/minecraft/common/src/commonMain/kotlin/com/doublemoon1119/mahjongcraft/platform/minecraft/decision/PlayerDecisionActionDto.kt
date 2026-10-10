package com.doublemoon1119.mahjongcraft.platform.minecraft.decision

import kotlinx.serialization.Serializable

/**
 * 呈現層可顯示的單一權威動作候選。
 *
 * @property token 動作候選的權威 token。
 * @property actionId 動作的穩定 ID。
 * @property referenceTileAssetKey 參考牌面 asset key。
 * @property previewTileAssetKeys 動作預覽牌面 asset key。
 * @property claimedTileIndex 預覽中需要強調的吃牌索引；不適用時為 null。
 * @property tileSelection 宣告後需要額外選牌時的選牌資料。
 */
@Serializable
data class PlayerDecisionActionDto(
    val token: String,
    val actionId: String,
    val referenceTileAssetKey: String?,
    val previewTileAssetKeys: List<String>,
    val claimedTileIndex: Int?,
    val tileSelection: PlayerDecisionActionTileSelectionDto?,
)
