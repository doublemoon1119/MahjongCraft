package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.DebugDevelopmentAssemblyContract
import org.koin.core.KoinApplication

/** 以 Fabric 的服務組合驗證 debug 服務接上本體。 */
class DebugDevelopmentAssemblyTest : DebugDevelopmentAssemblyContract() {
    override fun debugServices(): KoinApplication = debugKoinApplication()
}
