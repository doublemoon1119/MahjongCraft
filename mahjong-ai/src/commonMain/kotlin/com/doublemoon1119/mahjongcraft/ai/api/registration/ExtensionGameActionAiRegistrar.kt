package com.doublemoon1119.mahjongcraft.ai.api.registration

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiDefinition
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction

/** 擴充登記擴充動作 AI 處理方式時取得的登記介面；查詢、建立候選與凍結由 MahjongCraft 進行。 */
interface ExtensionGameActionAiRegistrar {
    /** 目前已登記動作型別的穩定類別名稱快照。 */
    val registrationKeys: Set<String>

    /**
     * 註冊一種擴充動作的 AI 命令候選建立方式。
     *
     * @param A 要登記的擴充動作型別。
     * @param definition 動作具體型別與 handler。
     * @throws IllegalStateException 註冊表已凍結。
     * @throws IllegalArgumentException 此動作型別已登記。
     */
    fun <A : ExtensionGameAction> register(definition: ExtensionGameActionAiDefinition<A>)
}
