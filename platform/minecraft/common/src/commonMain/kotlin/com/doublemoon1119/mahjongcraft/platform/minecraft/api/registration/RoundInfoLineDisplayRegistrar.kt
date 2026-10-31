package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoMessageDefinition

/** 擴充登記局況資訊行時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface RoundInfoLineDisplayRegistrar {
    /** 目前已登記局況資訊 key 的快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一個局況顯示行 key 的翻譯資訊。
     *
     * @param definition 顯示行的完整登記描述。
     */
    fun register(definition: RoundInfoLineDisplayDefinition)

    /**
     * 登記可供局況參數引用的規則文字。
     *
     * @param definition 訊息 ID 與翻譯 key 的對應描述。
     */
    fun register(definition: RoundInfoMessageDefinition)

    /**
     * 登記指定規則模組的局況資料 provider。
     *
     * @param definition provider 的完整登記描述。
     */
    fun register(definition: RoundInfoLineProviderDefinition)
}
