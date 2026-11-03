package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import kotlin.uuid.Uuid

/**
 * 建立世界角色時的公開資料；只在伺服器主執行緒使用，不可修改權威對局。
 *
 * @property world 席位所在且已載入的世界。
 * @property tableId 桌子 UUID。
 * @property matchId 場次 UUID。
 * @property playerId MahjongCraft AI UUID，與世界實體 UUID 不同。
 * @property character 本場固定的角色外觀。
 * @property seat 固定席位要求；不能自行更改桌子幾何。
 * @property participant 自動入房時已綁定的既有實體；自有角色為 null。借用能力不得換成其他實體。
 */
class FabricAiCharacterContext private constructor(
    val world: ServerWorld,
    val tableId: Uuid,
    val matchId: Uuid,
    val playerId: Uuid,
    val character: AiCharacterDefinition,
    val seat: SeatRequest,
    val participant: Entity?,
) {
    /** 由原生角色管理器建立請求，不提供第三方建立入口。 */
    companion object {
        /**
         * 保存一次能力要求的公開資料。
         *
         * @param world 席位世界。
         * @param tableId 桌子 UUID。
         * @param matchId 場次 UUID。
         * @param playerId AI UUID。
         * @param character 本場角色描述。
         * @param seat 本次固定席位。
         * @param participant 已由房間綁定的實體。
         * @return 交給能力提供者的請求。
         */
        @JvmSynthetic
        internal fun create(
            world: ServerWorld,
            tableId: Uuid,
            matchId: Uuid,
            playerId: Uuid,
            character: AiCharacterDefinition,
            seat: SeatRequest,
            participant: Entity? = null,
        ): FabricAiCharacterContext = FabricAiCharacterContext(
            world = world,
            tableId = tableId,
            matchId = matchId,
            playerId = playerId,
            character = character,
            seat = seat,
            participant = participant,
        )
    }
}
