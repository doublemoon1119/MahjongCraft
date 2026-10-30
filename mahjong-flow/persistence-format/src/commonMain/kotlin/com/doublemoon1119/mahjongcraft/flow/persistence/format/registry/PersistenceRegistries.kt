package com.doublemoon1119.mahjongcraft.flow.persistence.format.registry

import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.component.ComponentViewPersistenceRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile

/**
 * 權威狀態 persistence codec 使用的所有可擴充 DTO registry。
 *
 * @property ruleConfigs 規則配置 registry。
 * @property discardPiles 牌河 registry。
 * @property exhaustiveDrawReasons 流局原因 registry。
 * @property extensionGameActions 擴充動作 registry。
 * @property components 牌張與紀錄組件保存 registry。
 * @property componentViews 提交時已裁切投影的保存 registry，不能還原為權威組件。
 */
data class PersistenceRegistries(
    val ruleConfigs: PersistenceDtoRegistry<MahjongRuleConfig>,
    val discardPiles: PersistenceDtoRegistry<DiscardPile<*>>,
    val exhaustiveDrawReasons: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    val extensionGameActions: PersistenceDtoRegistry<ExtensionGameAction>,
    val components: ComponentPersistenceRegistry,
    val componentViews: ComponentViewPersistenceRegistry,
) {
    /** 所有 registry 中登記為可在精簡牌譜中壓縮的 type key。 */
    val replayCompactTypeKeys: Set<String>
        get() = ruleConfigs.replayCompactTypeKeys + discardPiles.replayCompactTypeKeys +
            exhaustiveDrawReasons.replayCompactTypeKeys + extensionGameActions.replayCompactTypeKeys

    /** 凍結所有 registry；凍結後不得新增 persistence mapper。 */
    fun freeze() {
        ruleConfigs.freeze()
        discardPiles.freeze()
        exhaustiveDrawReasons.freeze()
        extensionGameActions.freeze()
        components.freeze()
        componentViews.freeze()
    }
}

/** 建立尚未登記任何 mapper 的 persistence registries；各規則的 mapper 由該規則的 extension 登記。 */
fun emptyPersistenceRegistries(): PersistenceRegistries = PersistenceRegistries(
    ruleConfigs = PersistenceDtoRegistry(),
    discardPiles = PersistenceDtoRegistry(),
    exhaustiveDrawReasons = PersistenceDtoRegistry(),
    extensionGameActions = PersistenceDtoRegistry(),
    components = ComponentPersistenceRegistry(),
    componentViews = ComponentViewPersistenceRegistry(),
)
