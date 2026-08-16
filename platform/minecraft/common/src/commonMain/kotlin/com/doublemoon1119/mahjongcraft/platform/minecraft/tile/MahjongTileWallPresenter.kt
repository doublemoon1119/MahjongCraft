package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的正式牌牆呈現資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property dealerSeatIndex 目前莊家在 `TableState.players` 的固定座位 index。
 * @property structure 本局牌牆所有牌（含活牌與王牌）的面／墩／層結構座標，鍵為
 * [com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile.id]；空 map 代表這局結束、只需要清除
 * 舊牌，不需要建立新牌。
 * @property deadWallTileIds [structure] 之中屬於王牌區的牌 Uuid 子集合；[structure] 為空時可傳空集合。
 * @property diceCount 本次開門擲骰的骰子數量，用來換算擲骰動畫總長度、決定王牌區延遲移出開門位置的
 * 時機；未搭配擲骰時傳 `0`（此時實作不會排定王牌延遲移出）。
 */
data class MahjongTileWallPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val dealerSeatIndex: Int,
    val structure: Map<Uuid, TileWallPosition>,
    val deadWallTileIds: Set<Uuid>,
    val diceCount: Int,
)

/** 正式牌牆呈現請求的處理結果。 */
enum class MahjongTileWallPresentationResult {
    /** 已替換同桌舊牌並建立所有新牌（或 [MahjongTileWallPresentation.structure] 為空、只清除舊牌）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 其中一張牌無法加入世界；已回滾本次建立的牌。 */
    SPAWN_FAILED,
}

/**
 * 將權威牌牆結構呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端提供結構座標與莊家座位；entity、UUID 對齊、舊牌替換及版本 API 均由實作處理。比照
 * [com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter] 的 best-effort
 * 慣例。
 */
interface MahjongTileWallPresenter {
    /** 在指定桌面呈現整副牌牆；[MahjongTileWallPresentation.structure] 為空時等同只清除舊牌。 */
    fun present(presentation: MahjongTileWallPresentation): MahjongTileWallPresentationResult

    /** 清除指定桌子目前的正式牌牆用牌；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
