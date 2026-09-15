package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSource
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceProviderException
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證成員外觀的降級決策與一次性警告。 */
class RoomMemberAppearanceResolverTest {
    /** registry 回傳的來源直接對應呈現方式。 */
    @Test
    fun `passes through the resolved appearance`() {
        assertEquals(
            RoomMemberAppearanceResolver.Appearance.PlayerModel,
            resolver { RoomMemberAppearanceSource.PlayerModel }.resolve(PLAYER_ID, isAi = false).appearance,
        )
        assertEquals(
            RoomMemberAppearanceResolver.Appearance.Portrait,
            resolver { RoomMemberAppearanceSource.Portrait }.resolve(PLAYER_ID, isAi = true).appearance,
        )
    }

    /** 成功解析時不產生警告。 */
    @Test
    fun `reports no warning on a successful resolution`() {
        assertNull(resolver { RoomMemberAppearanceSource.Portrait }.resolve(PLAYER_ID, isAi = true).warning)
    }

    /** 解析失敗時 AI 降級為畫像、真人降級為玩家模型。 */
    @Test
    fun `falls back by player kind when the registry fails`() {
        assertEquals(
            RoomMemberAppearanceResolver.Appearance.Portrait,
            failingResolver().resolve(PLAYER_ID, isAi = true).appearance,
        )
        assertEquals(
            RoomMemberAppearanceResolver.Appearance.PlayerModel,
            failingResolver().resolve(PLAYER_ID, isAi = false).appearance,
        )
    }

    /** 解析失敗時回報失敗的 provider ID。 */
    @Test
    fun `reports the failing provider id`() {
        val warning = failingResolver("example:portraits").resolve(PLAYER_ID, isAi = false).warning

        val failure = assertIs<RoomMemberAppearanceResolver.Warning.ProviderFailed>(warning)
        assertEquals("example:portraits", failure.providerId)
    }

    /** 失敗不是來自特定 provider 時以 registry 作為識別字。 */
    @Test
    fun `attributes a plain failure to the registry`() {
        val resolver = RoomMemberAppearanceResolver(
            object : FakeRegistry() {
                override fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource = error("registry itself failed")
            },
        )

        val failure = assertIs<RoomMemberAppearanceResolver.Warning.ProviderFailed>(
            resolver.resolve(PLAYER_ID, isAi = false).warning,
        )
        assertEquals("registry", failure.providerId)
    }

    /** 同一個 provider 連續失敗只警告一次。 */
    @Test
    fun `warns once per failing provider`() {
        val resolver = failingResolver("example:portraits")

        assertIs<RoomMemberAppearanceResolver.Warning.ProviderFailed>(resolver.resolve(PLAYER_ID, isAi = false).warning)
        assertNull(resolver.resolve(PLAYER_ID, isAi = false).warning)
        assertNull(resolver.resolve(OTHER_PLAYER_ID, isAi = true).warning, "Expected no second warning for the same provider, even for another player.")
    }

    /** 不同 provider 各警告一次。 */
    @Test
    fun `warns separately for each failing provider`() {
        var providerId = "example:first"
        val resolver = RoomMemberAppearanceResolver(
            object : FakeRegistry() {
                override fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource = throw RoomMemberAppearanceSourceProviderException(providerId, IllegalStateException("boom"))
            },
        )

        assertIs<RoomMemberAppearanceResolver.Warning.ProviderFailed>(resolver.resolve(PLAYER_ID, isAi = false).warning)
        providerId = "example:second"
        assertIs<RoomMemberAppearanceResolver.Warning.ProviderFailed>(resolver.resolve(PLAYER_ID, isAi = false).warning)
    }

    /** 缺少 actor 預覽工廠時降級為畫像並警告一次。 */
    @Test
    fun `falls back to the portrait for an actor preview without a factory`() {
        val resolver = resolver { RoomMemberAppearanceSource.ActorPreview("example:cat") }

        val first = resolver.resolve(PLAYER_ID, isAi = false)
        assertEquals(RoomMemberAppearanceResolver.Appearance.Portrait, first.appearance)
        val warning = assertIs<RoomMemberAppearanceResolver.Warning.MissingActorPreviewFactory>(first.warning)
        assertEquals("example:cat", warning.actorKey)

        assertNull(resolver.resolve(PLAYER_ID, isAi = false).warning, "Expected only one warning per actor key.")
    }

    /** 不同 actor key 各警告一次。 */
    @Test
    fun `warns separately for each missing actor key`() {
        var actorKey = "example:cat"
        val resolver = RoomMemberAppearanceResolver(
            object : FakeRegistry() {
                override fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource = RoomMemberAppearanceSource.ActorPreview(actorKey)
            },
        )

        assertIs<RoomMemberAppearanceResolver.Warning.MissingActorPreviewFactory>(resolver.resolve(PLAYER_ID, isAi = false).warning)
        actorKey = "example:dog"
        assertIs<RoomMemberAppearanceResolver.Warning.MissingActorPreviewFactory>(resolver.resolve(PLAYER_ID, isAi = false).warning)
    }

    /** 建立回傳固定來源的 resolver。 */
    private fun resolver(source: () -> RoomMemberAppearanceSource) = RoomMemberAppearanceResolver(
        object : FakeRegistry() {
            override fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource = source()
        },
    )

    /** 建立一定會拋出 provider 例外的 resolver。 */
    private fun failingResolver(providerId: String = "example:provider") = RoomMemberAppearanceResolver(
        object : FakeRegistry() {
            override fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource = throw RoomMemberAppearanceSourceProviderException(providerId, IllegalStateException("boom"))
        },
    )

    /** 只有 resolve 會被使用的 registry 測試替身。 */
    private abstract class FakeRegistry : RoomMemberAppearanceSourceRegistry {
        override val registrationKeys: Set<String> get() = emptySet()

        override val isFrozen: Boolean get() = false

        override fun register(providerId: String, priority: Int, provider: RoomMemberAppearanceSourceProvider) = error("Unexpected registration")

        override fun freeze() = error("Unexpected freeze")
    }

    private companion object {
        /** 測試用的玩家 ID。 */
        val PLAYER_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

        /** 另一位測試用玩家的 ID。 */
        val OTHER_PLAYER_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000002")
    }
}
