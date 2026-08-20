package com.doublemoon1119.mahjongcraft.platform.fabric.server.tile

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTablePart
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.minecraft.block.BlockState
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.Properties
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/** 使用 Fabric 1.20.1 entity 呈現正式副露：把牌牆管理中的既有 entity 領走並移動，不重新生成。 */
@Single(binds = [MahjongMeldPresenter::class])
class FabricMahjongMeldPresenter(
    private val serverHolder: FabricServerHolder,
) : MahjongMeldPresenter {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 副露裡的每一張牌，UUID 都跟牌牆結構座標傳過來的那張牌完全同一個——理由跟
     * [FabricMahjongHandTilesPresenter.present] 完全一致，這裡只用 `World.getEntity` 依 UUID 找到
     * 既有 entity，直接改標記、改姿態（絕大多數牌是 [MahjongTilePose.FACE_UP]，副露牌面預設朝上可見，
     * 跟牌河同一套慣例；暗槓另外處理，見下方說明）、移動到副露位置——絕對不能另外 `spawnEntity`。
     *
     * [MahjongMeldPresentation.melds] 依宣告順序排列，第一組（最早宣告）的最右側那張牌外緣貼齊
     * [MahjongTileTableLayout.MELD_AREA_CORNER_OFFSET] 桌角邊界線，後續每組依序往玩家自己手牌方向
     * 排開（[cursorAlong] 由右往左依序累加每張已放置牌的實際外觀半寬，組間另外跳過 [MELD_GROUP_GAP]
     * 留白，讓不同副露在視覺上看得出分組）；單一組內鳴取牌（側身呈現）該落在哪個格位由
     * [sidewaysSlotIndex] 依 [MahjongMeldTileGroup.sourceDirection] 決定（吃固定來自上家＝組內最左；
     * 碰／明槓依上家／對家／下家分別對應最左／中／最右；暗槓沒有鳴牌來源，四張全部直立、不重排）；
     * 組內其餘牌依原始順序依序補進剩餘格位，這裡不特別講究哪張手牌具體對應哪個格位，只保證鳴取牌落
     * 在正確位置。
     *
     * 側身牌（[MahjongTileTableLayout.SIDEWAYS_YAW_OFFSET] 旋轉後）沿排列方向的外觀寬度是
     * [MahjongTileDimensions.TILE_HEIGHT]，比直立牌的 [MahjongTileDimensions.TILE_WIDTH] 更寬——
     * [cursorAlong] 依實際朝向的半寬累加，不是固定格寬，避免側身牌因為只分配到一般格寬的空間而跟隔壁
     * 牌外觀體積互相穿插；垂直於排列方向的外緣對齊則交給 [MahjongTileTableLayout.meldPlacement] 依朝向
     * 內部換算，確保直立與側身兩種朝向的牌，靠近桌緣那一側的外緣都貼齊同一條線，不會其中一種牌突出。
     *
     * [MeldType.ADDED_KAN]（加槓）另外處理：橫排寬度只算原碰的 3 格（沿用原碰的
     * [MahjongMeldTileGroup.sourceDirection]），升級時多出來的第 4 張（[List.last] 那張，對應
     * [Hand.upgradeToAddedKan] 固定 append 在 `tiles` 尾端的慣例）疊在原碰側身牌「靠近桌子中心」那
     * 一側（[MahjongTileTableLayout.meldPlacement] 的 `depthOffsetFromEdge`，往局部 Z 軸負向推一整張
     * 側身牌的寬度），同樣側身呈現，不佔用額外的橫向格位——使用者以示意圖確認過的方位，不是沿排列
     * 方向貼在旁邊，也不是疊在正上方。
     *
     * [MeldType.CLOSED_KAN]（暗槓）的姿態依 [MahjongMeldTileGroup.allTilesFaceDown] 另外處理，不是
     * 固定 [MahjongTilePose.FACE_UP]：`false`（該規則暗槓身份公開，例如日本麻將）時比照真實麻將慣例，
     * 兩端（組內第一、最後一格）蓋牌（[MahjongTilePose.FACE_DOWN]）、中間兩張攤牌；`true`（該規則暗槓
     * 完全不公開，例如台灣麻將）時四張全部蓋牌。找不到對應 UUID 的既有 entity 時該筆直接跳過並記
     * 警告 log，不中斷其餘牌的呈現，比照本介面 best-effort 的既有慣例。
     */
    override fun present(presentation: MahjongMeldPresentation): MahjongMeldPresentationResult {
        val world = resolveWorld(presentation.tableLocation) ?: return MahjongMeldPresentationResult.TABLE_NOT_FOUND
        val controllerPos = presentation.tableLocation.toBlockPos()
        val state = world.getBlockState(controllerPos)
        val table = resolveTable(world, controllerPos, state, presentation.tableId)
            ?: return MahjongMeldPresentationResult.TABLE_NOT_FOUND
        if (state.get(Properties.HORIZONTAL_FACING).toMahjongTableFacing() != presentation.tableFacing) {
            return MahjongMeldPresentationResult.TABLE_NOT_FOUND
        }

        var missingTileCount = 0

        fun placeTile(
            tileId: Uuid,
            alongOffsetFromCorner: Double,
            isSidewaysTile: Boolean,
            depthOffsetFromEdge: Double = 0.0,
            pose: MahjongTilePose = MahjongTilePose.FACE_UP,
        ) {
            val tile = world.getEntity(tileId.toJavaUuid()) as? MahjongTileEntity
            if (tile == null) {
                missingTileCount++
                logger.warn(
                    "publishMeldsUpdated tableId={} tileId={} skipped: no existing wall entity found to claim",
                    presentation.tableId,
                    tileId,
                )
                return
            }
            val placement = MahjongTileTableLayout.meldPlacement(
                controllerX = controllerPos.x,
                controllerY = controllerPos.y,
                controllerZ = controllerPos.z,
                tableFacing = presentation.tableFacing,
                seatIndex = presentation.seatIndex,
                alongOffsetFromCorner = alongOffsetFromCorner,
                isSidewaysTile = isSidewaysTile,
                depthOffsetFromEdge = depthOffsetFromEdge,
            )
            tile.assignToTable(presentation.tableId)
            tile.tilePose = pose
            tile.refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
        }

        var cursorAlong = 0.0
        presentation.melds.forEachIndexed { meldIndex, meld ->
            if (meldIndex > 0) cursorAlong += MELD_GROUP_GAP
            val addedTileId = if (meld.type == MeldType.ADDED_KAN) meld.tileIds.last() else null
            val baseTileIds = if (addedTileId != null) meld.tileIds.dropLast(1) else meld.tileIds
            val slotCount = baseTileIds.size
            val sidewaysSlot = meld.calledTileId?.let { sidewaysSlotIndex(meld.sourceDirection, slotCount) }
            val remainingTileIds = ArrayDeque(baseTileIds.filterNot { it == meld.calledTileId })
            val tileAtSlot = (0 until slotCount).map { slot ->
                if (slot == sidewaysSlot) meld.calledTileId!! else remainingTileIds.removeFirst()
            }
            var sidewaysAlongOffset: Double? = null
            for (slot in slotCount - 1 downTo 0) {
                val isSideways = slot == sidewaysSlot
                val halfWidth =
                    if (isSideways) MahjongTileDimensions.TILE_HEIGHT / 2.0 else MahjongTileDimensions.TILE_WIDTH / 2.0
                cursorAlong += halfWidth
                placeTile(tileAtSlot[slot], cursorAlong, isSidewaysTile = isSideways, pose = closedKanPose(meld, slot, slotCount))
                if (isSideways) sidewaysAlongOffset = cursorAlong
                cursorAlong += halfWidth + MahjongTileDimensions.TILE_SMALL_PADDING
            }
            if (addedTileId != null) {
                placeTile(addedTileId, sidewaysAlongOffset!!, isSidewaysTile = true, depthOffsetFromEdge = ADDED_KAN_DEPTH_OFFSET)
            }
        }
        table.markDirty()
        if (missingTileCount > 0) {
            logger.warn(
                "publishMeldsUpdated tableId={} presented with {} missing tile(s)",
                presentation.tableId,
                missingTileCount,
            )
            return MahjongMeldPresentationResult.SPAWN_FAILED
        }
        return MahjongMeldPresentationResult.PRESENTED
    }

    /**
     * 依鳴取來源方位，換算鳴取牌在副露組內（`0` 為組內最左，靠近副露區桌角錨點方向）該落在哪個格位——
     * 上家（[RelativeDirection.Left]，吃唯一合法來源）固定最左；對家（[RelativeDirection.Across]）
     * 固定格位 `1`，三張的碰跟四張的槓皆同（四張時是「偏左第二張」，不是幾何正中央）；下家
     * （[RelativeDirection.Right]）固定最右；暗槓（[RelativeDirection.Self]，沒有鳴牌來源）回傳
     * `null`，呼叫端據此判斷不重排、四張全部直立呈現。
     */
    private fun sidewaysSlotIndex(direction: RelativeDirection, tileCount: Int): Int? = when (direction) {
        RelativeDirection.Left -> SIDEWAYS_SLOT_LEFT
        RelativeDirection.Across -> SIDEWAYS_SLOT_ACROSS
        RelativeDirection.Right -> tileCount - 1
        RelativeDirection.Self -> null
    }

    /**
     * 換算暗槓（[MeldType.CLOSED_KAN]）組內第 [slot] 格該用的姿態；非暗槓固定
     * [MahjongTilePose.FACE_UP]。[MahjongMeldTileGroup.allTilesFaceDown] 為 `true`（該規則暗槓完全不
     * 公開）時四張全部蓋牌；為 `false`（該規則暗槓身份公開）時只蓋組內第一、最後一格（兩端），中間
     * 攤牌，比照真實麻將暗槓的傳統擺法。
     */
    private fun closedKanPose(meld: MahjongMeldTileGroup, slot: Int, slotCount: Int): MahjongTilePose {
        if (meld.type != MeldType.CLOSED_KAN) return MahjongTilePose.FACE_UP
        if (meld.allTilesFaceDown) return MahjongTilePose.FACE_DOWN
        return if (slot == 0 || slot == slotCount - 1) MahjongTilePose.FACE_DOWN else MahjongTilePose.FACE_UP
    }

    /**
     * 清除指定 controller 周圍且 table UUID 相符的所有正式管理中麻將牌——跟
     * [FabricMahjongTileWallPresenter.clear] 效果相同（都是清這張桌子的全部管理中麻將牌，不分子
     * 系統），保留成獨立方法只是維持介面對稱，呼叫端仍可能只想觸發副露這條路徑的清除。
     */
    override fun clear(tableId: Uuid, tableLocation: TableLocation): Int {
        val world = resolveWorld(tableLocation) ?: return 0
        val tiles = findManagedTiles(world, tableId, tableLocation.toBlockPos())
        tiles.forEach(MahjongTileEntity::discard)
        return tiles.size
    }

    /** 由版本無關 dimension ID 取得目前 server session 的世界。 */
    private fun resolveWorld(location: TableLocation): ServerWorld? {
        val identifier = Identifier.tryParse(location.dimensionId) ?: return null
        val worldKey = RegistryKey.of(RegistryKeys.WORLD, identifier)
        return serverHolder.current()?.getWorld(worldKey)
    }

    /** 驗證指定位置確實是 UUID 與朝向資料可用的 controller。 */
    private fun resolveTable(
        world: ServerWorld,
        controllerPos: BlockPos,
        state: BlockState,
        tableId: Uuid,
    ): MahjongTableBlockEntity? {
        if (state.block !is MahjongTableBlock || state.get(MahjongTableBlock.PART) != MahjongTablePart.BOTTOM_CENTER) {
            return null
        }
        if (!state.contains(Properties.HORIZONTAL_FACING)) return null
        return (world.getBlockEntity(controllerPos) as? MahjongTableBlockEntity)?.takeIf { table ->
            table.tableId == tableId
        }
    }

    /** 只查詢桌子結構附近並以同步 UUID 精確篩選，避免掃描整個 dimension。 */
    private fun findManagedTiles(
        world: ServerWorld,
        tableId: Uuid,
        controllerPos: BlockPos,
    ): List<MahjongTileEntity> = world.getEntitiesByClass(
        MahjongTileEntity::class.java,
        Box(controllerPos).expand(TILE_SEARCH_HORIZONTAL, TILE_SEARCH_VERTICAL, TILE_SEARCH_HORIZONTAL),
    ) { tile -> tile.managedTableId == tableId }

    /** 將版本無關 table location 轉回 Fabric 方塊座標。 */
    private fun TableLocation.toBlockPos(): BlockPos = BlockPos(x, y, z)

    /** 正式副露建立與查詢使用的固定參數。 */
    private companion object {
        /** controller 周圍查詢正式副露的水平半徑。 */
        const val TILE_SEARCH_HORIZONTAL: Double = 2.0

        /** controller 周圍查詢正式副露的垂直半徑。 */
        const val TILE_SEARCH_VERTICAL: Double = 2.0

        /**
         * 相鄰兩組副露之間額外跳過的世界距離相對 [MahjongTileDimensions.TILE_WIDTH] 的比例——只需要
         * 看得出分組的小縫隙，不是一整張牌的寬度（初版直接用一整張牌寬，遊戲內驗證後回報間距過大）。
         */
        const val MELD_GROUP_GAP_RATIO: Double = 0.3

        /** 相鄰兩組副露之間額外跳過的世界距離，見 [MELD_GROUP_GAP_RATIO]。 */
        const val MELD_GROUP_GAP: Double = MahjongTileDimensions.TILE_WIDTH * MELD_GROUP_GAP_RATIO

        /**
         * 加槓補上的第 4 張牌，垂直於排列方向（往桌子中心）額外推的距離——剛好是側身牌自己的外觀寬度
         * （[MahjongTileDimensions.TILE_WIDTH]，側身後這個方向的寬度）加一層
         * [MahjongTileDimensions.TILE_SMALL_PADDING] 縫隙，讓兩張側身牌前後相鄰、不重疊。
         */
        const val ADDED_KAN_DEPTH_OFFSET: Double = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING

        /** 上家鳴取（吃唯一合法來源）的側身牌組內格位：固定最左。 */
        const val SIDEWAYS_SLOT_LEFT: Int = 0

        /** 對家鳴取的側身牌組內格位：固定 `1`（碰／槓皆同，四張時是偏左第二張，不是幾何正中央）。 */
        const val SIDEWAYS_SLOT_ACROSS: Int = 1
    }
}
