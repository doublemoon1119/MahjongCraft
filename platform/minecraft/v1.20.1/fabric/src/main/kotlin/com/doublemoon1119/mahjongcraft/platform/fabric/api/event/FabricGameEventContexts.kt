package com.doublemoon1119.mahjongcraft.platform.fabric.api.event

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.GameEventContext
import net.minecraft.server.MinecraftServer
import java.util.concurrent.atomic.AtomicReference

/** 將事件情境轉換為目前有效的 Fabric Minecraft server。 */
object FabricGameEventContexts {
    /**
     * 取得事件產生時所屬的 Minecraft server。
     *
     * 情境只在產生它的伺服器 session 仍然有效時可轉換；換世界或伺服器停止後會拒絕，避免第三方
     * 把舊事件誤用到新的 server。
     *
     * @param context 要驗證並轉換的事件情境。
     * @return 產生事件時的 Minecraft server。
     * @throws IllegalStateException 情境沒有對應的有效 server session 時拋出。
     */
    @JvmStatic
    fun server(context: GameEventContext): MinecraftServer {
        val binding = activeBinding.get()
            ?: error("Game event context does not belong to an active Minecraft server session")
        check(binding.isValid(context)) {
            "Game event context does not belong to the active Minecraft server session"
        }
        return binding.serverProvider()
    }

    /**
     * 安裝目前 session 的轉換綁定；只由 Fabric session lifecycle 呼叫。
     * @param serverProvider 取得此 session 所屬的伺服器；不得改為動態查詢另一個 session。
     * @param isValid 驗證情境是否仍屬於有效 session。
     */
    @JvmSynthetic
    internal fun install(
        serverProvider: () -> MinecraftServer,
        isValid: (GameEventContext) -> Boolean,
    ) {
        activeBinding.set(Binding(serverProvider, isValid))
    }

    /** 清除目前 session 的轉換綁定；只由 Fabric session lifecycle 呼叫。 */
    @JvmSynthetic
    internal fun clear() {
        activeBinding.set(null)
    }

    /**
     * 目前 session 的 server 與情境驗證函式。
     *
     * @property serverProvider 取得綁定時所屬的 Minecraft server。
     * @property isValid 驗證情境是否仍屬於目前 session。
     */
    private class Binding(
        val serverProvider: () -> MinecraftServer,
        val isValid: (GameEventContext) -> Boolean,
    )

    /** 原子發布的目前 session 原生伺服器綁定。 */
    private val activeBinding = AtomicReference<Binding?>(null)
}
