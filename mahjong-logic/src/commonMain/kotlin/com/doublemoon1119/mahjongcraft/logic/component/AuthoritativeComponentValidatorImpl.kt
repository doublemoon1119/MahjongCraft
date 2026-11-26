package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 以正式登記檢查完整權威組件，不執行規則效果或補建缺少的值。
 *
 * @property moduleRegistry 提供對局規則的身分。
 * @property componentRegistry 正式組件、對象及生命週期定義。
 */
class AuthoritativeComponentValidatorImpl(
    private val moduleRegistry: MahjongModuleRegistry,
    private val componentRegistry: ComponentRegistry,
) : AuthoritativeComponentValidator {
    override fun validate(state: TableState) {
        state.requireRegisteredComponents(moduleRegistry.getModule(state.config).id, componentRegistry)
    }
}
