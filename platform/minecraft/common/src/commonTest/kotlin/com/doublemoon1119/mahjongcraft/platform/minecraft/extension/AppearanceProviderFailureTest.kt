package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceProviderException
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceProviderException
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistryImpl
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.uuid.Uuid

/** 驗證兩種外觀來源使用相同的失敗分類，不把取消與致命錯誤包成一般來源錯誤。 */
class AppearanceProviderFailureTest {
    /** 一般來源錯誤帶上可診斷的提供者身分，並保留原始原因。 */
    @Test
    fun `ordinary provider failures retain their diagnostic cause`() {
        val failure = IllegalStateException("Provider failed")
        val portrait = PlayerPortraitSourceRegistryImpl()
        portrait.register(PlayerPortraitSourceDefinition("example:portrait", provider = { throw failure }))
        val appearance = RoomMemberAppearanceSourceRegistryImpl()
        appearance.register(RoomMemberAppearanceSourceDefinition("example:appearance", provider = { throw failure }))
        val id = Uuid.random()

        assertSame(failure, assertFailsWith<PlayerPortraitSourceProviderException> { portrait.resolve(PlayerPortraitSourceContext(id, false)) }.cause)
        assertSame(failure, assertFailsWith<RoomMemberAppearanceSourceProviderException> { appearance.resolve(RoomMemberAppearanceContext(id, false)) }.cause)
    }

    /** 取消與致命錯誤原樣外傳，不被轉成可恢復的來源失敗。 */
    @Test
    fun `provider cancellation and fatal errors propagate unchanged`() {
        for (failure in listOf(CancellationException("Cancelled provider"), AssertionError("Fatal provider"))) {
            val portrait = PlayerPortraitSourceRegistryImpl()
            portrait.register(PlayerPortraitSourceDefinition("example:portrait", provider = { throw failure }))
            val appearance = RoomMemberAppearanceSourceRegistryImpl()
            appearance.register(RoomMemberAppearanceSourceDefinition("example:appearance", provider = { throw failure }))
            val id = Uuid.random()
            assertSame(failure, assertFailsWith<Throwable> { portrait.resolve(PlayerPortraitSourceContext(id, false)) })
            assertSame(failure, assertFailsWith<Throwable> { appearance.resolve(RoomMemberAppearanceContext(id, false)) })
        }
    }
}
