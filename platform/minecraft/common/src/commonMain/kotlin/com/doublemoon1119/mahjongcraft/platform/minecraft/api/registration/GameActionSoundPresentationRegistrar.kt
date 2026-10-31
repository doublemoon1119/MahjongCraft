package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundDefinition

/** 擴充登記動作音效時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface GameActionSoundPresentationRegistrar {
    /** 目前已登記規則模組與動作組合的穩定 key 快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 登記一項宣告式動作語音。 */
    fun register(definition: GameActionSoundDefinition)
}
