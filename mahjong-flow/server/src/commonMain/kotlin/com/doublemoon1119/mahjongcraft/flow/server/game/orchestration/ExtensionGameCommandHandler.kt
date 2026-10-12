package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import kotlin.uuid.Uuid

/**
 * 執行一種規則擴充命令。
 *
 * AI 送出的命令只在權威遊戲仍處於該 AI 決策所依據的狀態時才套用（見 [GameRepository.withExpectedGame]），因此 AI 可能送出的命令
 * 必須遵守：對該局最多做一次權威寫入，且事件、呈現與快照同步等副作用都在這次寫入成功之後才發布。寫入前發現局面已改變時，
 * 寫入會以例外中止，此時命令不得已經發布任何副作用；同一個命令寫入該局兩次會被視為違反契約並停止這次推進。
 *
 * @param C 此 handler 接受的擴充命令型別。
 */
interface ExtensionGameCommandHandler<C : ExtensionGameCommand> {
    /**
     * 執行指定玩家送出的強型別命令。
     *
     * @param gameId 欲執行命令的對局識別碼。
     * @param playerId 送出命令的玩家識別碼。
     * @param command 要執行的強型別擴充命令。
     * @return 命令執行成功或失敗的結果。
     */
    suspend fun execute(gameId: Uuid, playerId: Uuid, command: C): Outcome<Unit, GameError>
}
