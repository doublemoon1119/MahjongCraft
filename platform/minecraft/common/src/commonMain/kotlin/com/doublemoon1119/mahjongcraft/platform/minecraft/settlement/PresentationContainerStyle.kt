package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 可選容器外觀；預設完全透明且沒有框線或 padding。
 *
 * @property backgroundArgb 背景 ARGB。
 * @property borderArgb 邊框 ARGB。
 * @property borderWidth 邊框寬度。
 * @property padding 內容留白。
 * @property dividerArgb 分隔線 ARGB。
 * @property dividerWidth 分隔線寬度。
 */
data class PresentationContainerStyle(
    val backgroundArgb: Int = 0x00000000,
    val borderArgb: Int = 0x00000000,
    val borderWidth: Float = 0f,
    val padding: Float = 0f,
    val dividerArgb: Int = 0x00000000,
    val dividerWidth: Float = 0f,
)
