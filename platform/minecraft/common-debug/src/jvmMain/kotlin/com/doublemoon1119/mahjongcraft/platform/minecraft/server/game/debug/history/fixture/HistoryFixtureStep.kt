package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.logic.table.Wind

/**
 * 腳本中某一家的一個決定。
 *
 * @property wind 做決定的那一家在這一局的自風。
 * @property action 決定的內容。
 */
data class HistoryFixtureStep(val wind: Wind, val action: HistoryFixtureAction)
