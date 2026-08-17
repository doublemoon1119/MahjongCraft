package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的正式手牌呈現資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property handsBySeatIndex 依 `TableState.players` 固定座位 index 分組的手牌，每組依發牌順序排列，
 * 鍵為 [com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile.id]；空 map 代表這局結束、只需要
 * 清除舊牌，不需要建立新牌。
 */
data class MahjongHandTilesPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val handsBySeatIndex: Map<Int, List<Uuid>>,
)

/** 正式手牌呈現請求的處理結果。 */
enum class MahjongHandTilesPresentationResult {
    /** 已把同桌舊手牌換成本次要呈現的牌（或 [MahjongHandTilesPresentation.handsBySeatIndex] 為空、只清除舊牌）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /**
     * 其中一張以上的牌找不到對應的既有 entity 可以領走——手牌的 entity 應該早在牌牆生成時就已經
     * 存在（同一個 UUID），這裡不重新建立新 entity，只做「找到、改標記、移動」；找不到通常代表遊戲
     * 狀態跟世界狀態已經不一致。找不到的牌會被跳過，其餘牌仍照常呈現，不是整批回滾。
     */
    SPAWN_FAILED,
}

/**
 * 已由伺服器決定的摸牌位呈現資料——這位玩家目前摸到、尚未併入立牌或打出的那張牌。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property seatIndex 這位玩家在 `TableState.players` 的固定座位 index。
 * @property standingTileCount 這位玩家目前立牌張數（不含這張剛摸到的牌），供換算摸牌位偏移。
 * @property drawnTileId 剛摸到那張牌的 Uuid；為 `null` 代表清除既有摸牌位呈現。
 */
data class MahjongDrawnTilePresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val seatIndex: Int,
    val standingTileCount: Int,
    val drawnTileId: Uuid?,
)

/** 摸牌位呈現請求的處理結果。 */
enum class MahjongDrawnTilePresentationResult {
    /** 已把摸到的牌移動到摸牌位（或 [MahjongDrawnTilePresentation.drawnTileId] 為 `null`、只是沒有牌要放）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 摸到的牌找不到對應的既有 entity 可以領走，理由同 [MahjongHandTilesPresentationResult.SPAWN_FAILED]。 */
    SPAWN_FAILED,
}

/**
 * 將權威手牌分配呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端提供依座位分組的手牌；手牌裡每一張牌的 UUID 都跟牌牆結構座標裡的同一張牌完全相同——這副牌
 * 本來就是從牌牆摸出來分給玩家的，不是另外複製出一批新牌。實作因此不建立新 entity，而是找到
 * [com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter] 已經生成好的
 * 既有 entity，直接改標記、改姿態、移動位置。比照 [MahjongTileWallPresenter] 的 best-effort 慣例。
 * 手牌落地時全部蓋著（牌面不可見），揭不揭露交給既有的 `TableStateSnapshot` 可見性機制決定，這裡只
 * 負責把 entity 移到正確位置。
 */
interface MahjongHandTilesPresenter {
    /** 在指定桌面呈現所有玩家的手牌；[MahjongHandTilesPresentation.handsBySeatIndex] 為空時等同只清除舊牌。 */
    fun present(presentation: MahjongHandTilesPresentation): MahjongHandTilesPresentationResult

    /**
     * 呈現單一玩家的摸牌位——摸到的牌是同一批 wall-spawned entity 之一，不是新的一批牌，歸在「手牌
     * 區域」這個既有 presenter 裡最自然，不需要另開檔案。[MahjongDrawnTilePresentation.drawnTileId]
     * 為 `null` 時單純代表這次呼叫沒有牌要放，不需要清除任何既有 entity——摸牌位原本佔用的那張牌
     * 一定已經有新去處（併入立牌列表，或本身就是被丟的那張移去牌河），由呼叫端另外呼叫 [present]／
     * 牌河 presenter 處理，不會有「entity 留在摸牌位沒人管」需要這裡額外清除的情況。
     */
    fun presentDrawnTile(presentation: MahjongDrawnTilePresentation): MahjongDrawnTilePresentationResult

    /** 清除指定桌子目前的正式手牌；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
