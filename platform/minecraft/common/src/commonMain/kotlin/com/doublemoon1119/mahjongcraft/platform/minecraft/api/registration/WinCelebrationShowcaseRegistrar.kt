package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseDefinition

/** 擴充登記和牌演出時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface WinCelebrationShowcaseRegistrar {
    /** 目前已登記 cue key 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一個胡牌展示定義；重複 cue key 會失敗。
     *
     * @param definition 胡牌展示的完整註冊定義。
     */
    fun register(definition: WinCelebrationShowcaseDefinition)
}
