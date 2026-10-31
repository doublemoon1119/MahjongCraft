package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceConverter
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceConverter
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile

/**
 * 所有可擴充保存格式的唯讀轉換。
 *
 * MahjongCraft 呼叫 [PersistenceDtoDefinition] 的轉換函式時傳入，讓擴充的轉換函式能轉換巢狀的可擴充型別，
 * 例如牌河中的牌張組件；只能轉換，不能登記或凍結。
 */
interface PersistenceConverter {
    /** 規則配置。 */
    val ruleConfigs: PersistenceDtoConverter<MahjongRuleConfig>

    /** 牌河。 */
    val discardPiles: PersistenceDtoConverter<DiscardPile<*>>

    /** 流局原因。 */
    val exhaustiveDrawReasons: PersistenceDtoConverter<ExhaustiveDrawReason>

    /** 擴充動作。 */
    val extensionGameActions: PersistenceDtoConverter<ExtensionGameAction>

    /** 牌張與紀錄組件。 */
    val components: ComponentPersistenceConverter

    /** 提交時已裁切的組件投影。 */
    val componentViews: ComponentViewPersistenceConverter
}
