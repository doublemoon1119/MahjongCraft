package com.doublemoon1119.mahjongcraft.platform.fabric.api.event

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.GameEventContext
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

/** 驗證 Fabric 原生轉換 facade 的 Java 對外邊界。 */
class FabricGameEventContextsJavaVisibilityTest {
    /** Java 可透過靜態 facade 取得目前事件所屬的伺服器。 */
    @Test
    fun `server facade is exposed as a static Java method`() {
        val method = FabricGameEventContexts::class.java.getDeclaredMethod("server", GameEventContext::class.java)

        assertTrue(Modifier.isStatic(method.modifiers))
        assertTrue(Modifier.isPublic(method.modifiers))
    }

    /** session 生命週期的內部安裝與清除方法不會成為 Java 可呼叫 API。 */
    @Test
    fun `session lifecycle methods are hidden from Java`() {
        val lifecycleMethods = FabricGameEventContexts::class.java.declaredMethods.filter { method ->
            method.name.startsWith("install") || method.name.startsWith("clear")
        }

        assertTrue(lifecycleMethods.size == 2)
        assertTrue(lifecycleMethods.all { method -> method.isSynthetic })
    }
}
