package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 取得並驗證規則的逐張手牌描述，複製集合後依牌 UUID 穩定排序。
 *
 * 無額外權限且直立的描述省略，與不回報該張牌具有相同結果。先檢查重複與區域，不能藉預設值繞過驗證。
 *
 * @param tableState 權威桌況。
 * @return 不持有規則來源集合別名的有效描述。
 * @throws IllegalArgumentException 回報重複牌、錯誤區域或非公開的正面朝上姿態時。
 */
fun MahjongRuleModule<*>.resolveHandTileDescriptions(tableState: TableState): List<HandTileDescription> {
    val handIds = tableState.players.flatMap { it.hand.standingTiles }.mapTo(mutableSetOf()) { it.id }
    val seen = mutableSetOf<Uuid>()
    return describeHandTiles(tableState).toList().onEach { description ->
        require(seen.add(description.tileId)) { "Duplicate hand tile description: ${description.tileId}" }
        require(description.tileId in handIds) { "Tile is outside current hand areas: ${description.tileId}" }
        require(description.pose != HandTilePose.FACE_UP || description.visibility == HandTileVisibility.Public) {
            "A face-up hand tile must be public: ${description.tileId}"
        }
    }.filterNot { it.visibility == HandTileVisibility.Default && it.pose == HandTilePose.UPRIGHT }
        .sortedBy { it.tileId.toString() }
}
