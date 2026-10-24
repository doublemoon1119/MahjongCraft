package com.doublemoon1119.mahjongcraft.flow.api.event

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.jvm.JvmSynthetic
import kotlin.uuid.Uuid

/**
 * 一個已成立、可以對外通知的遊戲動作。
 *
 * @property actionId 動作的完整 namespaced ID。
 * @property actorPlayerId 執行動作的玩家 UUID。
 * @property kind 動作的中立分類。
 * @property tiles 動作公開的牌面；`null` 表示該位置的牌仍然隱藏。
 * @property supplementalTileCount 這個動作產生的補牌張數；不是補牌動作時為 `null`。
 * @property supplementalSourceId 補牌來源的完整 namespaced ID；沒有可公開來源時為 `null`。
 */
class PerformedAction private constructor(
    val actionId: String,
    val actorPlayerId: Uuid,
    val kind: PerformedActionKind,
    val tiles: List<PublicTileFace?>,
    val supplementalTileCount: Int?,
    val supplementalSourceId: String?,
) {
    init {
        NamespacedId.requireValid(actionId) { "Performed action ID must be namespaced: $actionId" }
        require(supplementalTileCount == null || supplementalTileCount >= 0) {
            "Supplemental tile count must not be negative: $supplementalTileCount"
        }
        supplementalSourceId?.let { sourceId ->
            NamespacedId.requireValid(sourceId) { "Supplemental source ID must be namespaced: $sourceId" }
        }
    }

    /** 事件系統內部建立資料的入口。 */
    internal companion object {
        /**
         * 建立已成立動作，並複製牌面清單。
         *
         * @param actionId 動作的完整 namespaced ID。
         * @param actorPlayerId 執行動作的玩家 UUID。
         * @param kind 動作的中立分類。
         * @param tiles 動作公開的牌面，`null` 表示隱藏牌。
         * @param supplementalTileCount 補牌張數；不是補牌動作時為 `null`。
         * @param supplementalSourceId 補牌來源的完整 namespaced ID；沒有時為 `null`。
         * @return 建立的已成立動作。
         */
        @JvmSynthetic
        fun create(
            actionId: String,
            actorPlayerId: Uuid,
            kind: PerformedActionKind,
            tiles: List<PublicTileFace?> = emptyList(),
            supplementalTileCount: Int? = null,
            supplementalSourceId: String? = null,
        ): PerformedAction = PerformedAction(
            actionId = actionId,
            actorPlayerId = actorPlayerId,
            kind = kind,
            tiles = ReadOnlyListSnapshot(tiles),
            supplementalTileCount = supplementalTileCount,
            supplementalSourceId = supplementalSourceId,
        )
    }
}
