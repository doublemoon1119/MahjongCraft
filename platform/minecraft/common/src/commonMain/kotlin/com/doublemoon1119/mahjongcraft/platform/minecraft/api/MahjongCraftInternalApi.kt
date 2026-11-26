package com.doublemoon1119.mahjongcraft.platform.minecraft.api

/**
 * 標示只供 MahjongCraft 自己的平台模組呼叫的公開宣告。
 *
 * 這類宣告因為要跨 MahjongCraft 的模組使用而必須是 public，但不屬於擴充可依賴的契約：行為與簽名可能隨時改變，
 * 從擴充呼叫也可能破壞 MahjongCraft 的內部狀態。使用處必須明確 opt-in。
 */
@RequiresOptIn(
    message = "This declaration is for MahjongCraft's own platform modules only.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
annotation class MahjongCraftInternalApi
