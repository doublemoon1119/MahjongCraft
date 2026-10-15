package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/** 平台為 `:mahjong-flow-client` 組裝的 client-side Koin 定義。 */
val flowClientModule = module {
    includes(flowCommonModule)

    single<ClientDecisionTimerStateStore>()
}
