package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import kotlinx.serialization.Polymorphic

/**
 * [MahjongRuleConfig] 的網路 DTO——刻意維持開放介面（不是 sealed），對應領域層本身就是開放介面、
 * 讓第三方能透過 [MahjongModuleRegistry] 註冊自己
 * 的規則模組這件事。序列化用的多型清單改用 [NetworkDtoRegistries] 動態組成，不是編譯期窮舉；
 * `@Polymorphic` 標記在介面上，讓任何用到這個型別的欄位都自動走 `SerializersModule` 查表，
 * 不需要在每個使用處各自標註。
 */
@Polymorphic
interface MahjongRuleConfigDto
