package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.client.game.ClientDecisionTimerStateStore
import com.doublemoon1119.mahjongcraft.flow.common.time.MonotonicClock
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.client.ClientMahjongStateStore
import org.koin.core.annotation.Module
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * Fabric client 使用的純狀態 Koin 定義。
 *
 * 此 module 會參與靜態 graph 驗證，因此只能包含不引用 Minecraft client API 的狀態物件；HUD 與其他
 * client-only adapter 不得加入此處，避免 dedicated server 載入環境限定類別。
 */
@Module
class FabricClientModule {
    /** 建立保存 client read-side 房間與遊戲快照的 store。 */
    @Single
    fun provideClientMahjongStateStore(
        @Provided networkRegistries: NetworkDtoRegistries,
    ): ClientMahjongStateStore = ClientMahjongStateStore(networkRegistries)

    /** 建立供 Fabric HUD 與網路接收端共用的 client-side 決策計時 store。 */
    @Single
    fun provideClientDecisionTimerStateStore(
        @Provided clock: MonotonicClock,
    ): ClientDecisionTimerStateStore = ClientDecisionTimerStateStore(clock)
}
