package com.doublemoon1119.mahjongcraft.ai

/**
 * [MahjongAiStrategyRegistry] 的預設實作。建構時是空的對照表，不預先塞入任何策略。要新增策略，
 * 直接呼叫 [register] 即可，不需要修改這個類別——連內建的 [RandomAiStrategy] 也是透過
 * [registerBuiltInAiStrategies] 呼叫 [register] 註冊進來，跟第三方策略走同一套流程。
 *
 * @property defaultKey 找不到對應策略時的退回 key；由呼叫端決定要退回哪個內建策略，這個類別本身
 *           不預設任何特定策略。
 */
class MahjongAiStrategyRegistryImpl(private val defaultKey: String) : MahjongAiStrategyRegistry {
    private val factoriesByKey = mutableMapOf<String, () -> MahjongAiStrategy>()

    override fun register(key: String, factory: () -> MahjongAiStrategy) {
        factoriesByKey[key] = factory
    }

    override fun resolve(key: String?): MahjongAiStrategy = (factoriesByKey[key] ?: factoriesByKey.getValue(defaultKey)).invoke()

    override fun getAllStrategyKeys(): Set<String> = factoriesByKey.keys
}
