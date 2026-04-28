package com.doublemoon1119.mahjongcraft.infrastructure.di

/**
 * 彙整所有基礎設施與應用層的 DI 模組。
 * 供外層平台（如 Minecraft）於啟動時進行統一註冊。
 */
val mahjongModules = listOf(
    infrastructureModule,
    applicationModule
)