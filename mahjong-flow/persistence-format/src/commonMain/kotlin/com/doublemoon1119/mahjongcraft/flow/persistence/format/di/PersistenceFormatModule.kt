package com.doublemoon1119.mahjongcraft.flow.persistence.format.di

import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * `:mahjong-flow-persistence-format` 擁有的 registry 與 codec Koin 定義。
 *
 * [PersistenceRegistries] 是聚合多個 registry 的資料型別，以 [emptyPersistenceRegistries] 建立空白初始內容交給 extension 登記。
 * [AuthoritativeStatePersistenceCodec] 只注入共用 registry，明確保留建構子的遷移註冊表與 Json 預設值，不讓網路使用的 Json
 * 或其他同型別定義改變存檔編解碼設定。
 */
val persistenceFormatModule = module {
    single<PersistenceRegistries> { emptyPersistenceRegistries() }
    single { AuthoritativeStatePersistenceCodec(registries = get()) }

    single<HistoryReplayProjectionRegistry>()
}
