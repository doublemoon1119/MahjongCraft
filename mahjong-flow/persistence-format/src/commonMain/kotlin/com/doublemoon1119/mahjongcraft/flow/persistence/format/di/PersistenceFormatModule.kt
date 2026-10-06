package com.doublemoon1119.mahjongcraft.flow.persistence.format.di

import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** `:mahjong-flow-persistence-format` 擁有的 registry 與 codec Koin 定義。 */
@Module
class PersistenceFormatModule {
    /** 建立空的共用歷史投影 registry；各規則的投影由 extension 登記，完成後由 bootstrap 集中凍結。
     * @return 可供擴充註冊及正式歷史讀取共用的唯一 registry。
     */
    @Single
    fun provideHistoryReplayProjectionRegistry(): HistoryReplayProjectionRegistry = HistoryReplayProjectionRegistry()

    /** 建立供 extension 註冊與 persistence adapter 共用的空 runtime registry。 */
    @Single
    fun providePersistenceRegistries(): PersistenceRegistries = emptyPersistenceRegistries()

    /** 建立使用 runtime persistence registries 的權威狀態 codec。 */
    @Single
    fun provideAuthoritativeStatePersistenceCodec(
        registries: PersistenceRegistries,
    ): AuthoritativeStatePersistenceCodec = AuthoritativeStatePersistenceCodec(registries)
}
