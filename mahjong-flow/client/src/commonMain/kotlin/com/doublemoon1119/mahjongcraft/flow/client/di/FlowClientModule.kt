package com.doublemoon1119.mahjongcraft.flow.client.di

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.di.flowCommonModule
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/** `:mahjong-flow-client` 擁有的 client-side Koin 定義。 */
val flowClientModule = module {
    includes(flowCommonModule)

    single<ClientDecisionTimerStateStore>()
}
