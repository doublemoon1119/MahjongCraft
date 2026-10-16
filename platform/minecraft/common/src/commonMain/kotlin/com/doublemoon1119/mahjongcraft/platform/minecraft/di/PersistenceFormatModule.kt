package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * 平台為 `:mahjong-flow-persistence-format` 組裝的 registry 與 codec Koin 定義。
 *
 * [PersistenceRegistries] 是聚合多個 registry 的資料型別，以 [emptyPersistenceRegistries] 建立空白初始內容交給 extension 登記。
 * [AuthoritativeStatePersistenceCodec] 只注入共用 registry，遷移註冊表使用建構子的內建預設值。
 */
val persistenceFormatModule = module {
    single<PersistenceRegistries> { emptyPersistenceRegistries() }
    single { AuthoritativeStatePersistenceCodec(registries = get()) }
}
