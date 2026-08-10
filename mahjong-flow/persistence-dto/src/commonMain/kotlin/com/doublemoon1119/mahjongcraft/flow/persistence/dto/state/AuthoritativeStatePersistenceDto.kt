package com.doublemoon1119.mahjongcraft.flow.persistence.dto.state

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceEnvelopeDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceSchema
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.TableStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.room.RoomPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.room.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.room.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.Uuid

/**
 * 同批保存所有 [Room] 與 [TableState] 的伺服器權威狀態 DTO。
 *
 * @property rooms 以 Room UUID 字串索引的等待階段狀態。
 * @property games 以 Game UUID 字串索引的進行中狀態。
 * @throws IllegalArgumentException 若索引與 DTO 內部 ID 不一致，或相同 ID 同時存在於 Room 與 Game。
 */
@Serializable
data class AuthoritativeStatePersistenceDto(
    val rooms: Map<String, RoomPersistenceDto>,
    val games: Map<String, TableStatePersistenceDto>,
) {
    init {
        require(rooms.all { (id, room) -> id == room.id }) { "Room persistence index must match its DTO ID" }
        require(games.all { (id, game) -> id == game.id }) { "Game persistence index must match its DTO ID" }
        require(rooms.keys.intersect(games.keys).isEmpty()) {
            "The same table ID must not exist as both a room and a game"
        }
    }
}

/** 將 Room 與 Game 集合轉換成同批提交的伺服器權威狀態 DTO。 */
fun createAuthoritativeStatePersistenceDto(
    rooms: Collection<Room>,
    games: Collection<TableState>,
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    dynamicRuleStateRegistry: PersistenceDtoRegistry<DynamicRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    json: Json = Json,
): AuthoritativeStatePersistenceDto {
    require(rooms.map(Room::id).distinct().size == rooms.size) { "Room IDs must be unique" }
    require(games.map(TableState::id).distinct().size == games.size) { "Game IDs must be unique" }

    return AuthoritativeStatePersistenceDto(
        rooms = rooms.associate { room ->
            room.id.toString() to room.toPersistenceDto(ruleConfigRegistry, json)
        },
        games = games.associate { game ->
            game.id.toString() to game.toPersistenceDto(
                ruleConfigRegistry,
                discardPileRegistry,
                playerRuleStateRegistry,
                dynamicRuleStateRegistry,
                exhaustiveDrawReasonRegistry,
                json,
            )
        },
    )
}

/** 將伺服器權威狀態 DTO 內的所有 Room 還原成以 UUID 索引的領域狀態。 */
fun AuthoritativeStatePersistenceDto.toRooms(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    json: Json = Json,
): Map<Uuid, Room> = rooms.values.associate { room -> Uuid.parse(room.id) to room.toDomain(ruleConfigRegistry, json) }

/** 將伺服器權威狀態 DTO 內的所有 Game 還原成以 UUID 索引的領域狀態。 */
fun AuthoritativeStatePersistenceDto.toGames(
    ruleConfigRegistry: PersistenceDtoRegistry<MahjongRuleConfig>,
    discardPileRegistry: PersistenceDtoRegistry<DiscardPile<*>>,
    playerRuleStateRegistry: PersistenceDtoRegistry<PlayerRuleState>,
    dynamicRuleStateRegistry: PersistenceDtoRegistry<DynamicRuleState>,
    exhaustiveDrawReasonRegistry: PersistenceDtoRegistry<ExhaustiveDrawReason>,
    json: Json = Json,
): Map<Uuid, TableState> = games.values.associate { game ->
    Uuid.parse(game.id) to game.toDomain(
        ruleConfigRegistry,
        discardPileRegistry,
        playerRuleStateRegistry,
        dynamicRuleStateRegistry,
        exhaustiveDrawReasonRegistry,
        json,
    )
}

/** 以目前 schema version 將伺服器權威狀態包裝成 [PersistenceEnvelopeDto]。 */
fun AuthoritativeStatePersistenceDto.toEnvelope(json: Json = Json): PersistenceEnvelopeDto = PersistenceEnvelopeDto(
    schemaVersion = PersistenceSchema.CURRENT_VERSION,
    state = json.encodeToJsonElement(AuthoritativeStatePersistenceDto.serializer(), this).jsonObject,
)

/**
 * 將已完成 migration 的目前 schema envelope 解碼成伺服器權威狀態 DTO。
 *
 * @throws IllegalArgumentException 若 envelope 尚未轉換成目前 schema version。
 */
fun PersistenceEnvelopeDto.decodeCurrentAuthoritativeState(json: Json = Json): AuthoritativeStatePersistenceDto {
    require(schemaVersion == PersistenceSchema.CURRENT_VERSION) {
        "Persistence envelope must use current schema version ${PersistenceSchema.CURRENT_VERSION}"
    }
    return json.decodeFromJsonElement(AuthoritativeStatePersistenceDto.serializer(), state)
}
