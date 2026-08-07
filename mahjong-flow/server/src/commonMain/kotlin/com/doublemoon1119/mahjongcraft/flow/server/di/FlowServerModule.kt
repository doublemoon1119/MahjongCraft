package com.doublemoon1119.mahjongcraft.flow.server.di

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * `:mahjong-flow-server` 的 Koin 模組。
 *
 * 絕大多數綁定靠 [ComponentScan] + 類別自身的 `@Factory` 標註自動完成。[mahjongAiStrategy] 是
 * 目前唯一一處手動綁定——`:mahjong-ai` 刻意不依賴 Koin（比照 `:mahjong-logic` 維持框架無關），
 * [RandomAiStrategy] 因此不會被這裡的 [ComponentScan] 掃到（套件字首不同），需要顯式提供。
 */
@Module(includes = [FlowCommonModule::class])
@ComponentScan("com.doublemoon1119.mahjongcraft.flow.server")
class FlowServerModule {
    @Single
    fun mahjongAiStrategy(): MahjongAiStrategy = RandomAiStrategy()
}
