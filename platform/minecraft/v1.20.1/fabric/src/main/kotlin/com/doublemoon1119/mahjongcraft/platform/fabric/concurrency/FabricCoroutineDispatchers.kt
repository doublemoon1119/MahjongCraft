package com.doublemoon1119.mahjongcraft.platform.fabric.concurrency

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.Single

/** [CoroutineDispatchers] 的 Fabric 實作。[main] 實際綁到伺服器主執行緒，見 [ServerThreadCoroutineDispatcher]。 */
@Single(binds = [CoroutineDispatchers::class])
class FabricCoroutineDispatchers(serverHolder: FabricServerHolder) : CoroutineDispatchers {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = ServerThreadCoroutineDispatcher(serverHolder)
}
