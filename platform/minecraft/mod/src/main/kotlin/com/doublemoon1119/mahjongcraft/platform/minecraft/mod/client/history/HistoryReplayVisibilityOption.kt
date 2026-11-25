package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys

/**
 * 牌譜回放場景中一項東西的顯示方式。人物可選全部四種，遊戲面板與桌上物件只用顯示與隱藏。
 *
 * @property translationKey 選項名稱的翻譯鍵。
 */
internal enum class HistoryReplayVisibilityOption(val translationKey: String) {
    /** 擋在鏡頭與桌子中央之間時半透明，其他時候照常顯示。 */
    AUTO(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_AUTO),

    /** 一律照常顯示。 */
    SHOWN(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_SHOWN),

    /** 一律半透明。 */
    TRANSLUCENT(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_TRANSLUCENT),

    /** 一律不畫。 */
    HIDDEN(MinecraftHistoryScreenKeys.REPLAY_VISIBILITY_HIDDEN),
}
