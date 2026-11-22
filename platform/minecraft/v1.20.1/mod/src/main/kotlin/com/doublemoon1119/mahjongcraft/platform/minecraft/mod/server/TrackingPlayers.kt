package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos

/** 查詢哪些玩家的客戶端目前看得到某個實體或位置，也就是伺服器會把它的變化送給哪些玩家。 */
object TrackingPlayers {
    /**
     * 目前看得到 [entity] 的玩家。
     *
     * 實體是玩家時，結果不一定包含玩家自己。
     *
     * @param entity 伺服器世界中的實體。
     * @return 看得到實體的玩家；實體不在追蹤範圍時為空。
     * @throws IllegalArgumentException 實體不在伺服器世界。
     */
    fun of(entity: Entity): Set<ServerPlayer> {
        val chunkSource = entity.level().chunkSource
        require(chunkSource is ServerChunkCache) { "Only supported on server levels" }
        val trackedEntity = chunkSource.chunkMap.entityMap.get(entity.id) ?: return emptySet()
        return trackedEntity.seenBy.mapTo(mutableSetOf()) { it.player }
    }

    /**
     * 目前看得到 [pos] 所在區塊的玩家。
     *
     * @param level 位置所在的伺服器世界。
     * @param pos 要查詢的方塊位置。
     * @return 看得到該區塊的玩家。
     */
    fun of(level: ServerLevel, pos: BlockPos): List<ServerPlayer> = level.chunkSource.chunkMap.getPlayers(ChunkPos(pos), false)
}
