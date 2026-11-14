package com.doublemoon1119.mahjongcraft.testing.flow.bundled

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryRegistries
import kotlin.random.Random

/**
 * 測試用：與 [bundledHeadlessHistoryRegistries] 相同，但不論要求哪種 AI 策略，一律改用以 [seed] 決定選擇的隨機出牌 AI。
 *
 * 只檢查流程能否走完的測試用它省下 AI 思考時間；搭配同一個數字的對局種子時，整場對局每次都相同。所有座位共用一個亂數來源，
 * 因此只能用在依固定順序逐一呼叫 AI 的推進方式，例如直接執行器。台灣麻將流局時莊家連莊，隨機出牌幾乎不會和牌，對局可能打不完，
 * 不可用在台灣麻將的整場對局。
 *
 * @param seed 決定隨機出牌選擇的數字。
 * @return 改用隨機出牌 AI 的 registry。
 */
fun seededRandomMoveHeadlessHistoryRegistries(seed: Int): HeadlessHistoryRegistries {
    val core = bundledCoreExtensionRegistries()
    val strategy = RandomAiStrategy(core.gameActionAiRegistry, Random(seed))
    val strategies = object : MahjongAiStrategyRegistry by core.aiStrategyRegistry {
        override fun resolve(key: String?): MahjongAiStrategy = strategy
    }
    return bundledHeadlessHistoryRegistries(core).copy(aiStrategyRegistry = strategies)
}
