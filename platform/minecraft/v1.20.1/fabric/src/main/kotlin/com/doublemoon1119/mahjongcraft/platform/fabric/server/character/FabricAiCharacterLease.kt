package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterLease
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterMountOwnership
import net.minecraft.server.world.ServerWorld
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 將原生角色包成可測試的接管協定；角色及所有權在建立時固定。
 *
 * @property rider 已驗證角色。
 * @property world 接管時的世界，不跨世界追蹤或重新生成借用角色。
 * @property ownership 接管時固定的所有權。
 * @property tracking 解除接管時清除公開互動標記。
 */
internal class FabricAiCharacterLease(
    override val rider: FabricAiCharacterActor,
    private val world: ServerWorld,
    override val ownership: AiCharacterOwnership,
    private val tracking: FabricAiCharacterTracking,
) : AiCharacterLease<FabricAiCharacterActor> {
    override val entityId: Uuid = rider.entity.uuid.toKotlinUuid()

    /** 接管前的載具；框架只解除自己後來建立的乘坐關係。 */
    private val originalVehicle = rider.entity.vehicle

    override fun isValid(): Boolean {
        val entity = rider.entity
        return entity.isAlive &&
            entity.uuid.toKotlinUuid() == entityId &&
            entity.world === world &&
            world.getEntity(entity.uuid) === entity &&
            world.chunkManager.isChunkLoaded(entity.blockPos.x shr 4, entity.blockPos.z shr 4)
    }

    override fun prepare(): Boolean {
        val prepared = rider.handle.prepare()
        check(rider.entity.uuid.toKotlinUuid() == entityId && rider.entity.world === world) { "Character identity changed during preparation" }
        return prepared
    }

    override fun release() {
        tracking.broadcast(rider.entity, false)
        val vehicle = rider.entity.vehicle
        if (AiCharacterMountOwnership.shouldDetach(originalVehicle, rider.assignedVehicle, vehicle)) {
            rider.entity.stopRiding()
            if (vehicle is MahjongStoolSeatEntity && !vehicle.hasPassengers()) vehicle.discard()
        }
        try {
            rider.handle.lookAt(null)
        } finally {
            rider.handle.release()
        }
    }

    override fun discardOwned() {
        check(ownership == AiCharacterOwnership.OWNED) { "Borrowed character cannot be discarded" }
        rider.entity.discard()
    }
}
