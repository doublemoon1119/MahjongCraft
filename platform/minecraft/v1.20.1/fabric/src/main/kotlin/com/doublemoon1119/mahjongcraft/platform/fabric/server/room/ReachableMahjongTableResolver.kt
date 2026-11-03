package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.platform.fabric.block.LocatedMahjongTable
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableLookup
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.DimensionChunkKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSpace
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableInteractionReach
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameMode

/**
 * 依玩家目前實際可互動範圍，從 [TableLocationRegistry] 找出可選的麻將桌。
 *
 * 1.20.1 沒有公開的「這個方塊在玩家可觸範圍內嗎」API（那是 1.20.5＋ reach-distance attribute
 * 改版後才加入的 `PlayerEntity.canInteractWithBlock`）；原版右鍵方塊互動本身在伺服器端也幾乎不驗證
 * 距離，純粹信任客戶端。這裡自行依 gamemode 套用原版已知的固定可觸距離（創造 5 格、生存／冒險 4.5
 * 格、旁觀視同無限），逼近「跟右鍵桌子手勢一致」的效果。日後升級到 1.20.5 以上時，應改用
 * `player.canInteractWithBlock(pos, additionalRange)` 取代這裡手動維護的距離常數。
 *
 * @property tableLocationRegistry 目前 server session 的桌子位置索引。
 */
class ReachableMahjongTableResolver(
    private val tableLocationRegistry: TableLocationRegistry,
) {
    /**
     * 列出 [player] 目前可互動範圍內的所有麻將桌，供 Tab 補全建議候選。
     *
     * 只在玩家所在 chunk 與周圍 8 個 chunk 內查詢已知位置索引，避免掃描整個 dimension。
     *
     * @param player 要列出桌子候選的玩家。
     * @return 玩家可互動且已載入的麻將桌。
     */
    internal fun findReachable(player: ServerPlayerEntity): List<LocatedMahjongTable> {
        val world = player.serverWorld
        val dimensionId = world.registryKey.value.toString()
        val chunkX = player.blockX shr CHUNK_COORDINATE_SHIFT
        val chunkZ = player.blockZ shr CHUNK_COORDINATE_SHIFT
        val reachDistance = reachDistanceFor(player)

        return (-1..1).asSequence()
            .flatMap { dx -> (-1..1).asSequence().map { dz -> DimensionChunkKey(dimensionId, chunkX + dx, chunkZ + dz) } }
            .flatMap { key -> tableLocationRegistry.getByChunk(key).asSequence() }
            .mapNotNull { entry ->
                val pos = BlockPos(entry.location.x, entry.location.y, entry.location.z)
                val table = MahjongTableLookup.find(world, pos) ?: return@mapNotNull null
                table.takeIf { distanceTo(player, pos) <= reachDistance }
            }
            .toList()
    }

    /**
     * 解析玩家指定的座標；桌子不存在或超出可互動範圍時回傳 `null`。
     *
     * @param player 執行互動的玩家。
     * @param pos 指定的 controller 方塊座標。
     * @return 符合位置與距離條件的桌子；否則為 null。
     */
    internal fun resolve(player: ServerPlayerEntity, pos: BlockPos): LocatedMahjongTable? {
        val table = MahjongTableLookup.find(player.serverWorld, pos) ?: return null
        return table.takeIf { distanceTo(player, pos) <= reachDistanceFor(player) }
    }

    /**
     * 依原版已知的固定可觸距離，換算玩家目前 gamemode 對應的可觸距離（方塊數）。
     *
     * @param player 要檢查遊戲模式的玩家。
     * @return 該模式的可觸距離。
     */
    private fun reachDistanceFor(player: ServerPlayerEntity): Double = when (player.interactionManager.gameMode) {
        GameMode.CREATIVE -> CREATIVE_REACH_DISTANCE
        GameMode.SPECTATOR -> Double.MAX_VALUE
        else -> SURVIVAL_REACH_DISTANCE
    }

    /**
     * 玩家座標到麻將桌固定範圍（[MahjongTableSpace] 的控制層與擺牌層，不是單一控制方塊）最近點的直線距離。
     * 範圍以控制方塊為中心對稱，桌子朝向不影響結果，也不依賴桌子實際由哪些方塊組成。
     *
     * @param player 量測距離的玩家。
     * @param pos 桌子控制方塊座標。
     * @return 玩家到桌子範圍最近點的直線距離。
     */
    private fun distanceTo(player: ServerPlayerEntity, pos: BlockPos): Double = TableInteractionReach.distance(player.x, player.y, player.z, pos.x, pos.y, pos.z)

    private companion object {
        /** 方塊座標轉換為 16×16 chunk 座標的位移量，與 [TableLocation] 一致。 */
        const val CHUNK_COORDINATE_SHIFT: Int = 4

        /** 原版創造模式的可觸距離（方塊數）。 */
        const val CREATIVE_REACH_DISTANCE: Double = TableInteractionReach.CREATIVE

        /** 原版生存／冒險模式的可觸距離（方塊數）。 */
        const val SURVIVAL_REACH_DISTANCE: Double = TableInteractionReach.SURVIVAL
    }
}
