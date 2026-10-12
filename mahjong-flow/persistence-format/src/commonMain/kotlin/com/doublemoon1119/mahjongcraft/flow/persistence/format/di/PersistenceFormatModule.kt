package com.doublemoon1119.mahjongcraft.flow.persistence.format.di

import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** `:mahjong-flow-persistence-format` 擁有的 registry 與 codec Koin 定義。 */
@Module
@ComponentScan("com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay")
class PersistenceFormatModule {
    /**
     * 建立供 extension 註冊與 persistence adapter 共用的空 runtime registry。
     *
     * [PersistenceRegistries] 是聚合多個 registry 的資料型別，不是可由元件掃描直接建立的服務；
     * 明確呼叫 [emptyPersistenceRegistries] 以保留其空白初始內容。
     */
    @Single
    fun providePersistenceRegistries(): PersistenceRegistries = emptyPersistenceRegistries()

    /**
     * 建立使用執行期存檔註冊表的權威狀態編解碼器。
     *
     * 只注入共用 registry，明確保留建構子的遷移註冊表與 Json 預設值，
     * 不讓網路使用的 Json 或其他同型別定義改變存檔編解碼設定。
     *
     * @param registries 內建與擴充規則共用的存檔型別註冊表。
     * @return 使用指定註冊表的權威狀態編解碼器。
     */
    @Single
    fun provideAuthoritativeStatePersistenceCodec(
        registries: PersistenceRegistries,
    ): AuthoritativeStatePersistenceCodec = AuthoritativeStatePersistenceCodec(registries)
}
