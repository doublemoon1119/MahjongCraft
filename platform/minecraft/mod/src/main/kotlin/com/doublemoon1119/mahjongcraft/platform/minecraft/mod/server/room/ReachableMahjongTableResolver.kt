package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.LocatedMahjongTable
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongTableLookup
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.blockInteractionReach
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.DimensionChunkKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSpace
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableInteractionReach
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType

/**
 * 依玩家目前實際可互動範圍，從 [TableLocationRegistry] 找出可選的麻將桌。
 *
 * - 可觸距離取玩家目前與方塊互動的距離（[blockInteractionReach]），旁觀模式視同無限。
 * - 距離量到整張桌子範圍最近的點，不是單一控制方塊，與右鍵桌子任何一部分的手勢一致。
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
    internal fun findReachable(player: ServerPlayer): List<LocatedMahjongTable> {
        val world = player.serverLevel()
        val dimensionId = world.dimension().location().toString()
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
    internal fun resolve(player: ServerPlayer, pos: BlockPos): LocatedMahjongTable? {
        val table = MahjongTableLookup.find(player.serverLevel(), pos) ?: return null
        return table.takeIf { distanceTo(player, pos) <= reachDistanceFor(player) }
    }

    /**
     * 玩家目前的可觸距離（方塊數）。
     *
     * @param player 要檢查的玩家。
     * @return 可觸距離。
     */
    private fun reachDistanceFor(player: ServerPlayer): Double = tableReachDistance(player.gameMode.gameModeForPlayer) { blockInteractionReach(player) }

    /**
     * 玩家座標到麻將桌固定範圍（[MahjongTableSpace] 的控制層與擺牌層，不是單一控制方塊）最近點的直線距離。
     * 範圍以控制方塊為中心對稱，桌子朝向不影響結果，也不依賴桌子實際由哪些方塊組成。
     *
     * @param player 量測距離的玩家。
     * @param pos 桌子控制方塊座標。
     * @return 玩家到桌子範圍最近點的直線距離。
     */
    private fun distanceTo(player: ServerPlayer, pos: BlockPos): Double = TableInteractionReach.distance(player.x, player.y, player.z, pos.x, pos.y, pos.z)

    private companion object {
        /** 方塊座標轉換為 16×16 chunk 座標的位移量，與 [TableLocation] 一致。 */
        const val CHUNK_COORDINATE_SHIFT: Int = 4
    }
}

/**
 * 依遊戲模式決定選桌時的可觸距離：旁觀模式不限距離，其他模式使用玩家與方塊互動的距離。
 *
 * @param gameType 玩家目前的遊戲模式。
 * @param blockReach 取得玩家與方塊互動的距離；旁觀模式不呼叫。
 * @return 可觸距離（方塊數）。
 */
internal fun tableReachDistance(gameType: GameType, blockReach: () -> Double): Double = if (gameType == GameType.SPECTATOR) Double.MAX_VALUE else blockReach()
