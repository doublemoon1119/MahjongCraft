package com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile

/** 擴充登記權威狀態保存格式時取得的各類登記介面；編碼、解碼與凍結由 MahjongCraft 進行。 */
interface PersistenceRegistrars {
    /** 規則配置。 */
    val ruleConfigs: PersistenceDtoRegistrar<MahjongRuleConfig>

    /** 牌河。 */
    val discardPiles: PersistenceDtoRegistrar<DiscardPile<*>>

    /** 流局原因。 */
    val exhaustiveDrawReasons: PersistenceDtoRegistrar<ExhaustiveDrawReason>

    /** 擴充動作。 */
    val extensionGameActions: PersistenceDtoRegistrar<ExtensionGameAction>

    /** 牌張與紀錄組件。 */
    val components: ComponentPersistenceRegistrar

    /** 提交時已裁切的組件投影，不能還原為權威組件。 */
    val componentViews: ComponentViewPersistenceRegistrar
}
