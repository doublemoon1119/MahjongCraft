package com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository

import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.component.AuthoritativeComponentValidator
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.requireRegisteredComponents
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries

/**
 * 建立驗證組件的測試倉庫，不省略定義、對象或必要值檢查。
 *
 * 此輔助使用固定的測試規則 ID。需要驗證依規則 ID 選擇生命週期的案例，應明確組裝正式驗證器。
 *
 * @param store 測試使用的權威交易儲存。
 * @param componentRegistry 此測試實際登記的組件，預設使用內建定義。
 * @return 連接正式 repository 與組件檢查的測試實例。
 */
fun componentValidatedGameRepository(
    store: AuthoritativeStateStore,
    componentRegistry: ComponentRegistry = bundledCoreExtensionRegistries().componentRegistry,
): GameRepositoryImpl = GameRepositoryImpl(
    store = store,
    componentValidator = AuthoritativeComponentValidator { state ->
        state.requireRegisteredComponents("test:repository", componentRegistry)
    },
)
