package com.doublemoon1119.mahjongcraft.flow.server.game.repository

import kotlin.uuid.Uuid

/**
 * 在 [GameRepository.withExpectedGame] 範圍內對同一局做了第二次權威寫入。
 *
 * 第一次寫入可能已經提交，因此不能重試，也不代表已回滾。
 *
 * @param gameId 被寫入兩次的遊戲。
 */
class ExpectedGameWrittenTwiceException(gameId: Uuid) : IllegalStateException("Game $gameId was written more than once under an expected game")
