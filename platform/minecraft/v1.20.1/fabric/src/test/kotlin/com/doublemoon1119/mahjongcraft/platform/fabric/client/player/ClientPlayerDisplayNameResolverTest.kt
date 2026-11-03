package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterClientStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSyncSnapshot
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 房間、通用文字與歷史沿同一名稱服務，AI 不進入真人 profile 查詢。 */
class ClientPlayerDisplayNameResolverTest {
    /** 活動角色、排除者與歷史描述各用授權來源，歷史不跟著目前改名。 */
    @Test
    fun `live excluded and historical actor names use their authorized snapshots`() {
        val table = Uuid.random()
        val host = Uuid.random()
        val actor = Uuid.random()
        val excluded = Uuid.random()
        val match = Uuid.random()
        val state = ClientMahjongStateStore(DefaultNetworkDtoRegistries())
        state.applyRoomSnapshot(
            table,
            RoomSnapshot(
                id = table,
                hostId = host,
                gameConfig = GameConfig(FakeMahjongRuleConfig()),
                playerIds = listOf(host, actor),
                readyPlayerIds = listOf(actor),
                aiPlayerIds = listOf(actor),
                canStart = true,
                isHost = true,
                isInRoom = true,
                excludedPlayerIds = listOf(excluded),
                excludedAiPlayerIds = listOf(excluded),
            ),
        )
        val characters = AiCharacterClientStateStore()
        val session = Uuid.random()
        characters.beginSession(session)
        characters.apply(
            AiCharacterSyncSnapshot(
                sessionId = session,
                revision = 1,
                tableId = table,
                isAvailable = true,
                catalogue = emptyList(),
                players = mapOf(actor to AiCharacterSnapshot("example:actor", null, "Live actor")),
                excludedPlayers = mapOf(excluded to AiCharacterSnapshot("example:actor", null, "Excluded actor")),
            ),
        )
        characters.rememberHistory(match, mapOf(actor to AiCharacterSnapshot("example:actor", null, "Archived actor")))
        val profiles = ClientPlayerProfileResolver()
        val names = ClientPlayerDisplayNameResolver(state, profiles, characters)
        assertEquals("Live actor", names.resolve(table, actor.toString()))
        assertEquals("Live actor", names.resolve(null, actor.toString(), true))
        assertEquals("Excluded actor", names.resolve(table, excluded.toString(), true))
        assertEquals("Archived actor", names.historical(actor, match, true, listOf(actor)))
        assertNull(profiles.resolvedProfile(actor))
        assertNull(profiles.resolvedProfile(excluded))
    }
}
