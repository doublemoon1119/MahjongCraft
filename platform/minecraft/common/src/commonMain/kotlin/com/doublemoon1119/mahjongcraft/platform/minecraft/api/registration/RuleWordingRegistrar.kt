package com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.RuleWordingDefaultDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.RuleWordingDefinition

/** 擴充登記共用文字的中立用語與規則說法時取得的登記介面；查詢與凍結由 MahjongCraft 進行。 */
interface RuleWordingRegistrar {
    /** 目前已登記的「default/文字 ID」與「規則 ID/文字 ID」穩定快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一段共用文字的中立用語；凍結後或文字 ID 重複時拒絕。
     *
     * @param definition 文字 ID 與中立用語。
     */
    fun register(definition: RuleWordingDefaultDefinition)

    /**
     * 登記規則對一段共用文字的說法；凍結後或同一規則與文字 ID 重複時拒絕。
     *
     * @param definition 規則、文字 ID 與規則說法。
     */
    fun register(definition: RuleWordingDefinition)
}
