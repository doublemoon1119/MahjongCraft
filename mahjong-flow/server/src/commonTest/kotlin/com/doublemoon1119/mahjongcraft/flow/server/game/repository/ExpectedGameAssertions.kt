package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.uuid.Uuid

/**
 * 以 [gameId] 目前的遊戲為預期遊戲執行 [command]，確認預期遊戲比對成功，且儲存庫累計新增一次權威寫入。
 *
 * 用來確認 AI 可送出的主要命令符合「只做一次權威寫入」的契約。
 *
 * @param T 命令的回傳型別。
 * @param gameId 用於取得預期遊戲及建立條件式提交範圍的識別碼。
 * @param command 要執行的命令。
 * @return 命令的執行結果。
 */
suspend fun <T> FakeGameRepository.runAsSingleWrite(gameId: Uuid, command: suspend () -> T): T {
    val expected = assertNotNull(getGame(gameId), "The game must exist before the command")
    val writesBefore = writeCount
    val result = withExpectedGame(gameId, expected, command)
    assertIs<ExpectedGameResult.Applied<T>>(result, "The command must run on the expected game")
    assertEquals(1, writeCount - writesBefore, "The command must write the game exactly once")
    return result.value
}
