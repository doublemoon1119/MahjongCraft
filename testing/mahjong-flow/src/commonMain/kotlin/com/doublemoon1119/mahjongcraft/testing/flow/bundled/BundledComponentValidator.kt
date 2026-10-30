package com.doublemoon1119.mahjongcraft.testing.flow.bundled

import com.doublemoon1119.mahjongcraft.logic.component.AuthoritativeComponentValidator
import com.doublemoon1119.mahjongcraft.logic.component.AuthoritativeComponentValidatorImpl

/**
 * 使用完整內建登記驗證測試保存的對局，與正式讀寫套用相同規格。
 *
 * @return 已連接規則與組件登記的驗證器。
 */
fun bundledComponentValidator(): AuthoritativeComponentValidator {
    val registries = bundledCoreExtensionRegistries()
    return AuthoritativeComponentValidatorImpl(registries.moduleRegistry, registries.componentRegistry)
}
