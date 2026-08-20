package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.DimensionChunkKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameMode
import org.koin.core.annotation.Single
import kotlin.math.sqrt

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
@Single
class ReachableMahjongTableResolver(
    private val tableLocationRegistry: TableLocationRegistry,
) {
    /**
     * 列出 [player] 目前可互動範圍內的所有麻將桌，供 Tab 補全建議候選。
     *
     * 只在玩家所在 chunk 與周圍 8 個 chunk 內查詢已知位置索引，避免掃描整個 dimension。
     */
    fun findReachable(player: ServerPlayerEntity): List<MahjongTableBlockEntity> {
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
                val table = world.getBlockEntity(pos) as? MahjongTableBlockEntity ?: return@mapNotNull null
                table.takeIf { distanceTo(player, pos) <= reachDistance }
            }
            .toList()
    }

    /** 解析玩家指定的座標；桌子不存在或超出可互動範圍時回傳 `null`。 */
    fun resolve(player: ServerPlayerEntity, pos: BlockPos): MahjongTableBlockEntity? {
        val table = player.serverWorld.getBlockEntity(pos) as? MahjongTableBlockEntity ?: return null
        return table.takeIf { distanceTo(player, pos) <= reachDistanceFor(player) }
    }

    /** 依原版已知的固定可觸距離，換算玩家目前 gamemode 對應的可觸距離（方塊數）。 */
    private fun reachDistanceFor(player: ServerPlayerEntity): Double = when (player.interactionManager.gameMode) {
        GameMode.CREATIVE -> CREATIVE_REACH_DISTANCE
        GameMode.SPECTATOR -> Double.MAX_VALUE
        else -> SURVIVAL_REACH_DISTANCE
    }

    /** 玩家座標到方塊中心的直線距離。 */
    private fun distanceTo(player: ServerPlayerEntity, pos: BlockPos): Double {
        val playerPos = player.pos
        val dx = pos.x + BLOCK_CENTER - playerPos.x
        val dy = pos.y + BLOCK_CENTER - playerPos.y
        val dz = pos.z + BLOCK_CENTER - playerPos.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    private companion object {
        /** 方塊座標轉換為 16×16 chunk 座標的位移量，與 [TableLocation] 一致。 */
        const val CHUNK_COORDINATE_SHIFT: Int = 4

        /** 方塊中心的座標偏移。 */
        const val BLOCK_CENTER: Double = 0.5

        /** 原版創造模式的可觸距離（方塊數）。 */
        const val CREATIVE_REACH_DISTANCE: Double = 5.0

        /** 原版生存／冒險模式的可觸距離（方塊數）。 */
        const val SURVIVAL_REACH_DISTANCE: Double = 4.5
    }
}
