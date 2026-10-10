package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 桌面局況顯示的一行內容。
 *
 * [key] 與參數由 Minecraft 呈現註冊決定，規則模組本身不需要依賴這個資料模型。
 *
 * @property key 這一行使用的呈現識別碼。
 * @property args 這一行的格式化參數。
 */
data class RoundInfoLine(
    val key: String,
    val args: List<RoundInfoArgument> = emptyList(),
)
