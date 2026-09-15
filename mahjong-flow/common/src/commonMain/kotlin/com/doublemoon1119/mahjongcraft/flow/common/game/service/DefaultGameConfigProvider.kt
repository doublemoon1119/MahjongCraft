package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig

/**
 * 提供建立新房間時採用的完整預設遊戲設定。
 *
 * 呼叫端必須在每次建立房間時重新取得設定，不應將回傳值視為可供多個房間共同修改的草稿。
 */
fun interface DefaultGameConfigProvider {
    /** 建立一份供新房間使用的完整遊戲設定。 */
    fun create(): GameConfig
}
