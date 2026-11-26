package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.ClientPlayerProfileResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.CustomSkinPlayer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.SkinTextures
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.player.modelPartShown
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.defaultSkinTextures
import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.player.PlayerModelPart
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 牌譜回放場景中的真人玩家模型，不加入世界。
 *
 * 外觀與牌譜頭像相同，皮膚依序取：
 *
 * 1. 玩家在線上時，玩家列表的皮膚。
 * 2. 正版帳號，原生皮膚服務載入的皮膚。
 * 3. 都拿不到時，原版依 UUID 決定的預設皮膚。
 *
 * - 每一幀重新查詢，皮膚載入完成後自動換上。手臂粗細與披風跟著同一份皮膚資料。
 * - 帽子、外套等外層部位依玩家自己的外觀設定顯示。玩家不在目前世界中、看不到他的設定時全部顯示。
 *
 * @param world 目前客戶端世界。
 * @property playerId 玩家識別碼。
 * @param profile 玩家 profile。只用來建立模型。
 * @property profiles 皮膚、手臂款式與披風的來源。
 */
internal class HistoryReplayPlayerEntity(
    world: ClientLevel,
    private val playerId: Uuid,
    profile: GameProfile,
    private val profiles: ClientPlayerProfileResolver,
) : CustomSkinPlayer(world, profile) {
    override fun customSkin(): SkinTextures {
        val fallback = defaultSkinTextures(playerId.toJavaUuid())
        return SkinTextures(
            texture = profiles.resolvedSkinTexture(playerId) ?: fallback.texture,
            modelName = profiles.resolvedSkinModel(playerId) ?: fallback.modelName,
            cape = profiles.resolvedCapeTexture(playerId),
        )
    }

    override fun isModelPartShown(part: PlayerModelPart): Boolean = modelPartShown(playerId, part)
}
