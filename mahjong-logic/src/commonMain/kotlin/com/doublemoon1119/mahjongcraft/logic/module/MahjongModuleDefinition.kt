package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import kotlin.reflect.KClass

/**
 * 保存規則配置型別、穩定模組 ID 與建立函式；解析配置時才建立規則模組。
 *
 * @param T 規則配置的具體型別。
 * @property configClass 規則配置的具體型別。
 * @property id 跨執行期穩定的規則模組識別碼。
 * @property factory 解析配置時建立規則模組的工廠。
 */
data class MahjongModuleDefinition<T : MahjongRuleConfig>(
    val configClass: KClass<T>,
    val id: String,
    val factory: (T, String) -> MahjongRuleModule<T>,
)
