package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.RuleCatalogueExit
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueConfigSource
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

/**
 * 依歷史對局保存的設定建立規則一覽的開啟脈絡。
 *
 * 設定能在本地解出時使用它的規則與設定；解不出時改用摘要記錄的規則，設定標為無法取得，由玩家自行選擇一般說明。
 *
 * @param decodedRuleModuleId 由保存設定解出的規則；解不出時為 null。
 * @param decodedRuleConfig 由保存設定解出的規則設定；解不出時為 null。
 * @param summaryRuleModuleId 對局摘要記錄的規則；沒有摘要時為 null。
 * @return 開啟脈絡；兩者都沒有合法規則時為 null。
 */
internal fun historyRuleCatalogueContext(
    decodedRuleModuleId: String?,
    decodedRuleConfig: MahjongRuleConfig?,
    summaryRuleModuleId: String?,
): RuleCatalogueBrowseContext? {
    if (decodedRuleModuleId != null && decodedRuleConfig != null && NamespacedId.isValid(decodedRuleModuleId)) {
        return RuleCatalogueBrowseContext(
            ruleModuleId = decodedRuleModuleId,
            source = RuleCatalogueConfigSource.HISTORY,
            config = decodedRuleConfig,
        )
    }
    val ruleModuleId = summaryRuleModuleId?.takeIf(NamespacedId::isValid) ?: return null
    return RuleCatalogueBrowseContext(ruleModuleId = ruleModuleId, source = RuleCatalogueConfigSource.HISTORY, config = null)
}

/**
 * 從歷史規則設定頁開啟的規則一覽：經由瀏覽 session 返回同一個規則設定頁，保留捲動位置。
 *
 * 規則一覽被其他原因移除時照常結束瀏覽；瀏覽在規則一覽開著時結束，規則一覽關回遊戲。
 *
 * @property session 開啟規則一覽的歷史瀏覽。
 * @property settings 返回時回到的規則設定頁。
 */
internal class HistoryRuleCatalogueExit(
    private val session: HistoryBrowseSession,
    private val settings: Screen,
) : RuleCatalogueExit {
    override val returnScreen: Screen = settings

    override fun back(client: MinecraftClient) {
        session.backFromRuleCatalogue(settings)
    }

    override fun removed(screen: Screen) {
        session.removed(screen)
    }

    override fun isValid(client: MinecraftClient): Boolean = session.isOpen
}
