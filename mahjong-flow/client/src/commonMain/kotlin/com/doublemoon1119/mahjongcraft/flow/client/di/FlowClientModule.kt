package com.doublemoon1119.mahjongcraft.flow.client.di

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/** `:mahjong-flow-client` 擁有的 client-side Koin 定義。 */
@Module
class FlowClientModule {
    /** 建立供 client adapter 與畫面共用的決策計時狀態。 */
    @Single
    fun provideClientDecisionTimerStateStore(
        @Provided clock: MonotonicClock,
    ): ClientDecisionTimerStateStore = ClientDecisionTimerStateStore(clock)
}
