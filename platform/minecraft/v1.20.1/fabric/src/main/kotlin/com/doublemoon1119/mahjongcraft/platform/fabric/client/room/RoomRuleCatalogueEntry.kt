package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.RuleCatalogueExit
import com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue.RuleCatalogueScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueBrowseContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueConfigSource
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

/**
 * 依設定頁目前顯示的內容建立規則一覽的開啟脈絡：與房間目前設定不同時標為尚未套用的草稿。
 *
 * @param ruleModuleId 設定頁顯示的規則。
 * @param displayed 設定頁顯示的設定，含尚未套用的修改。
 * @param authoritative 房間目前的設定。
 * @return 規則一覽的開啟脈絡。
 */
internal fun roomRuleCatalogueContext(
    ruleModuleId: String,
    displayed: GameConfig,
    authoritative: GameConfig,
): RuleCatalogueBrowseContext = RuleCatalogueBrowseContext(
    ruleModuleId = ruleModuleId,
    source = if (displayed != authoritative) RuleCatalogueConfigSource.ROOM_DRAFT else RuleCatalogueConfigSource.ROOM,
    config = displayed.ruleConfig,
)

/**
 * 從房間設定頁開啟的規則一覽：返回同一個房間畫面，保留草稿、分類與捲動；牌桌已無法使用時關回遊戲。
 *
 * @property room 開啟規則一覽的房間畫面。
 */
internal class RoomRuleCatalogueExit(private val room: RoomScreen) : RuleCatalogueExit {
    override val returnScreen: Screen = room

    override fun back(client: MinecraftClient) {
        client.setScreen(if (room.isTableStillAvailable()) room else null)
    }

    override fun isValid(client: MinecraftClient): Boolean = room.isTableStillAvailable()
}

/**
 * 目前畫面是否正在顯示某張牌桌的房間，包括從房間開啟、返回後會回到房間的規則一覽。
 *
 * @param screen 目前畫面。
 * @return 房間畫面或從房間開啟的規則一覽時為 true。
 */
internal fun isShowingRoom(screen: Screen?): Boolean = screen is RoomScreen || (screen as? RuleCatalogueScreen)?.returnScreen is RoomScreen
