package com.doublemoon1119.mahjongcraft.flow.persistence.dto.di

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceCodec
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** `:mahjong-flow-persistence-dto` 擁有的 registry 與 codec Koin 定義。 */
@Module
class PersistenceDtoModule {
    /** 建立供 extension 註冊與 persistence adapter 共用的 runtime registry。 */
    @Single
    fun providePersistenceRegistries(): PersistenceRegistries = buildBuiltInPersistenceRegistries()

    /** 建立使用 runtime persistence registries 的權威狀態 codec。 */
    @Single
    fun provideAuthoritativeStatePersistenceCodec(
        registries: PersistenceRegistries,
    ): AuthoritativeStatePersistenceCodec = AuthoritativeStatePersistenceCodec(registries)
}
