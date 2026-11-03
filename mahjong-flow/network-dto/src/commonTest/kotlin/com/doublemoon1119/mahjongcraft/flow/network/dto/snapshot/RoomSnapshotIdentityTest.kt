package com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 排除來源在真正的網路格式往返後保留，不能掛在不存在的成員上。 */
class RoomSnapshotIdentityTest {
    /** 保留成員編號與來源 UUID 的不同語意，並在解碼後拒絕未授權的身分對照。 */
    @Test
    fun `excluded identities survive serialization and reject invalid ownership`() {
        val registries = DefaultNetworkDtoRegistries().apply { registerBundledNetworkDtos() }
        val json = NetworkJson(registries)
        val host = Uuid.random()
        val member = Uuid.random()
        val identity = RoomParticipantIdentity("test:entity", Uuid.random())
        val snapshot = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(RiichiRuleConfig()),
            playerIds = listOf(host),
            admission = RoomAdmissionState(excludedParticipants = mapOf(identity to member)),
        ).toSnapshot(host)
        val dto = snapshot.toDto(registries)
        val restored = json.decodeFromString(RoomSnapshotDto.serializer(), json.encodeToString(RoomSnapshotDto.serializer(), dto))
        assertEquals(snapshot, restored.toDomain(registries))
        assertEquals(identity.subjectId.toString(), restored.excludedParticipantIdentities.getValue(member.toString()).subjectId)
        assertFailsWith<IllegalArgumentException> { restored.copy(isHost = false).toDomain(registries) }
        assertFailsWith<IllegalArgumentException> { restored.copy(excludedPlayerIds = emptyList()).toDomain(registries) }
        val invalid = mapOf(member.toString() to RoomParticipantIdentityDto("not namespaced", identity.subjectId.toString()))
        assertFailsWith<IllegalArgumentException> { restored.copy(excludedParticipantIdentities = invalid).toDomain(registries) }
    }
}
