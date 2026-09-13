package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
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
 * @property assemblyStructure 牌牆完成組裝、尚未開門時的面／墩／層格位，鍵為 [IdentifiedTile.id]；
 *                             空 map 代表這局結束，只需清除舊牌。
 * @property finalLayout 規則決定的開門後最終抽象實體布局；牌張集合必須與 [assemblyStructure] 相同。
 * @property deadWallTileIds [finalLayout] 之中屬於王牌區的牌 Uuid 子集合；空布局時可傳空集合。
 * @property diceCount 本次開門擲骰的骰子數量，只用來換算開門前的擲骰動畫總長度。
 * @property animateOpening 是否從 [assemblyStructure] 播放至 [finalLayout]；恢復既有桌況時傳 `false`。
 * @property revealedTileIds [deadWallTileIds] 之中，牌牆建立當下就該立即公開翻面的牌 Uuid 子集合
 *                     （例如日麻開局就翻開的第一張寶牌指示牌，見 `TileWallRevealable`）——實作會在
 *                     切換至 [finalLayout] 的同一個時機點把這些牌的姿態改成正面朝上，其餘王牌維持牌背朝上；
 *                     不支援此概念的規則（或尚未有任何牌需要公開，例如空王牌）傳空集合即可。
 */
data class MahjongTileWallPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val dealerSeatIndex: Int,
    val assemblyStructure: Map<Uuid, TileWallPosition>,
    val finalLayout: TileWallPhysicalLayout,
    val deadWallTileIds: Set<Uuid>,
    val diceCount: Int,
    val animateOpening: Boolean = diceCount > 0,
    val revealedTileIds: Set<Uuid> = emptySet(),
)

/**
 * 已由規則解析完成、等待在既有牌牆 entity 上播放的布局 transition。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property dealerSeatIndex 本局莊家在固定座位列表中的 index。
 * @property stacksPerSide 牌牆每面的墩數。
 * @property phases 規則提供的來源、目的與先後階段。
 * @property startGameTime 第一個階段可開始播放的絕對 server game time。
 */
data class MahjongTileWallTransitionPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val dealerSeatIndex: Int,
    val stacksPerSide: Int,
    val phases: List<PhysicalWallLayoutTransitionPhase>,
    val startGameTime: Long,
)

/** 既有牌牆 entity transition 的處理結果。 */
enum class MahjongTileWallTransitionResult {
    /** 所有需要的動畫已原子排入既有 entity 佇列。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 至少一張必要的既有管理中牌 entity 不存在。 */
    TILE_NOT_FOUND,

    /** 至少一段抽象 placement 無法建立安全路徑。 */
    INVALID_PATH,
}

/** 正式牌牆呈現請求的處理結果。 */
enum class MahjongTileWallPresentationResult {
    /** 已替換同桌舊牌並建立所有新牌（或 [MahjongTileWallPresentation.finalLayout] 為空、只清除舊牌）。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 其中一張牌無法加入世界；已回滾本次建立的牌。 */
    SPAWN_FAILED,

    /** 初始 assembly 與 final layout 無法建立安全開門路徑。 */
    INVALID_PATH,
}

/**
 * 將權威牌牆結構呈現於 Minecraft 世界的版本 adapter 邊界。
 *
 * 呼叫端提供結構座標與莊家座位；entity、UUID 對齊、舊牌替換及版本 API 均由實作處理。
 * 比照 [MahjongDiceRollPresenter] 的 best-effort 慣例。
 */
interface MahjongTileWallPresenter {
    /** 在指定桌面呈現整副牌牆；[MahjongTileWallPresentation.finalLayout] 為空時等同只清除舊牌。 */
    fun present(presentation: MahjongTileWallPresentation): MahjongTileWallPresentationResult

    /** 將權威 transition 原子排入指定桌子的既有牌牆 entity。 */
    fun presentTransition(presentation: MahjongTileWallTransitionPresentation): MahjongTileWallTransitionResult

    /**
     * 把 [revealedTileIds] 對應的既有王牌 entity 姿態改成正面朝上，其餘管理中的王牌不受影響——用於
     * 牌牆建立之後才追加公開的牌（例如日麻槓牌後翻開新的寶牌指示牌），跟 [present] 開局那次的初始
     * 公開（[MahjongTileWallPresentation.revealedTileIds]）是兩條獨立的時機，理由見
     * `GamePresentationPublisher.publishWallTilesRevealed` KDoc。冪等：重複呼叫同一批 id 沒有
     * 副作用。
     *
     * @param revealedTileIds 本次新增公開翻面的牌 Uuid 集合；實作只處理這一批，重複 ID 仍須保持冪等。
     * @return 找不到對應 entity 的張數；比照本介面 best-effort 慣例，找不到的牌會被跳過。
     */
    fun revealDeadWallTiles(tableId: Uuid, tableLocation: TableLocation, revealedTileIds: Set<Uuid>): MahjongTileWallPresentationResult

    /** 清除指定桌子目前的正式牌牆用牌；回傳實際移除數量。 */
    fun clear(tableId: Uuid, tableLocation: TableLocation): Int
}
