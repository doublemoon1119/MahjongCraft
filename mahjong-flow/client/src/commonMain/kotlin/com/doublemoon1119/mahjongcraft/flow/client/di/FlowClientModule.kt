package com.doublemoon1119.mahjongcraft.flow.client.di

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/** `:mahjong-flow-client` 擁有的 client-side Koin 定義。 */
@Module
class FlowClientModule {
    /**
     * 建立供客戶端資料接收與時間推算共用的決策計時狀態。
     *
     * @param clock 客戶端接收同步時使用的單調時鐘。
     * @return 共用的決策計時狀態儲存元件。
     */
    @Single
    fun provideClientDecisionTimerStateStore(
        @Provided clock: MonotonicClock,
    ): ClientDecisionTimerStateStore = ClientDecisionTimerStateStore(clock)
}
