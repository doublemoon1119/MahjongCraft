package com.doublemoon1119.mahjongcraft.platform.forge.debug

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.DebugDevelopmentAssemblyContract
import org.koin.core.KoinApplication

/** 以 Forge 的服務組合驗證 debug 服務接上本體。 */
class DebugDevelopmentAssemblyTest : DebugDevelopmentAssemblyContract() {
    override fun debugServices(): KoinApplication = debugKoinApplication()
}
