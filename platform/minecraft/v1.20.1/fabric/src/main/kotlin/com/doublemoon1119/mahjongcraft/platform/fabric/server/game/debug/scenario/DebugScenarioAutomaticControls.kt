package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game

/**
 * 讓情境載入接續這一桌目前的本局自動操作設定。
 *
 * 情境會重建整個 [Game]，若直接採用新建物件的預設值，版本號就會在對局 ID 不變的情況下倒退，
 * 客戶端保存的個人快照依單調版本號規則拒收，本局自動操作從此無法再更新。因此載入一律沿用玩家
 * 已啟用的控制，並讓版本號繼續往前一格，與換局時的處理方式一致。
 */
internal fun DebugGameScenarioResult.continueAutomaticControls(currentGame: Game): DebugGameScenarioResult = copy(
    game = game.copy(
        enabledAutomaticControlIdsByPlayerId = currentGame.enabledAutomaticControlIdsByPlayerId,
        automaticControlRevision = currentGame.automaticControlRevision + 1L,
    ),
)
