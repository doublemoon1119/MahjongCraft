package com.doublemoon1119.mahjongcraft.platform.fabric.client.catalogue

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen

/** 規則一覽關閉後回到哪裡，以及開啟它的畫面是否仍然有效；由開啟規則一覽的一方提供。 */
internal interface RuleCatalogueExit {
    /** 返回時回到的畫面；直接關回遊戲時為 null。 */
    val returnScreen: Screen?

    /**
     * 按下返回、關閉或 Esc 時離開規則一覽。
     *
     * @param client 目前的客戶端。
     */
    fun back(client: MinecraftClient)

    /**
     * 規則一覽被移除時通知，包括返回、被其他畫面取代與斷線。
     *
     * @param screen 被移除的規則一覽。
     */
    fun removed(screen: Screen) = Unit

    /**
     * 開啟規則一覽的來源是否仍然有效；失效時規則一覽直接關回遊戲。
     *
     * @param client 目前的客戶端。
     * @return 仍可返回原畫面時為 true。
     */
    fun isValid(client: MinecraftClient): Boolean = true
}

/** 一般說明入口：返回時關回遊戲。 */
internal object CloseRuleCatalogueToGame : RuleCatalogueExit {
    override val returnScreen: Screen? = null

    override fun back(client: MinecraftClient) {
        client.setScreen(null)
    }
}
