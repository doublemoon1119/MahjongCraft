package doublemoon.mahjongcraft.game

import net.minecraft.entity.Entity
import net.minecraft.server.network.ServerPlayerEntity

interface GamePlayer {

    /**
     * 遊戲玩家的實體
     * */
    val entity: Entity

    /**
     * 遊戲玩家的顯示用名稱
     * */
    val displayName: String
        get() = entity.displayName.string

    /**
     * 遊戲玩家的名稱
     * */
    val name: String
        get() = entity.name.string

    /**
     * 玩家的 uuid 字串
     * */
    val uuid: String
        get() = entity.uuidAsString

    /**
     * 這個玩家是否是真的玩家
     * */
    val isRealPlayer: Boolean
        get() = entity is ServerPlayerEntity
}