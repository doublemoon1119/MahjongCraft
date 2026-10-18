package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPhysicalLayout
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
 * @property sideLayout 規則指定並依本局莊家解析的牌牆面與桌邊對應。
 * @property assemblyStructure 牌牆完成組裝、尚未開門時的面／墩／層格位，鍵為 [IdentifiedTile.id]；
 *                             空 map 代表這局結束，只需清除舊牌。
 * @property finalLayout 規則決定的開門後最終抽象實體布局；牌張集合必須與 [assemblyStructure] 相同。
 * @property deadWallTileIds [finalLayout] 之中屬於王牌區的牌 Uuid 子集合；空布局時可傳空集合。
 * @property openingDiceCounts 各輪開門擲骰的骰子數量，只用來換算開門前的擲骰動畫總長度。
 * @property animateOpening 是否從 [assemblyStructure] 播放至 [finalLayout]；恢復既有桌況時傳 `false`。
 * @property revealedTileIds [deadWallTileIds] 之中，牌牆建立當下就該立即公開翻面的牌 Uuid 子集合
 * （例如日麻開局就翻開的第一張寶牌指示牌，見 `TileWallRevealable`）——實作會在
 * 切換至 [finalLayout] 的同一個時機點把這些牌的姿態改成正面朝上，其餘王牌維持牌背朝上；
 * 不支援此概念的規則（或尚未有任何牌需要公開，例如空王牌）傳空集合即可。
 */
data class MahjongTileWallPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val sideLayout: MahjongTileWallSideLayout,
    val assemblyStructure: Map<Uuid, TileWallPosition>,
    val finalLayout: TileWallPhysicalLayout,
    val deadWallTileIds: Set<Uuid>,
    val openingDiceCounts: List<Int>,
    val animateOpening: Boolean = true,
    val revealedTileIds: Set<Uuid> = emptySet(),
)
