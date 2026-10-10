package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/** RoomScreen 可安全解讀的成員外觀來源。 */
sealed interface RoomMemberAppearanceSource {
    /** 使用玩家模型。 */
    data object PlayerModel : RoomMemberAppearanceSource

    /** 使用玩家頭像。 */
    data object Portrait : RoomMemberAppearanceSource

    /**
     * 使用註冊的角色預覽。
     *
     * @property actorKey 角色預覽的 Namespaced ID。
     */
    data class ActorPreview(
        val actorKey: String,
    ) : RoomMemberAppearanceSource {
        init {
            NamespacedId.requireValid(actorKey) { "Room actor preview key must be namespaced: $actorKey" }
        }
    }
}
