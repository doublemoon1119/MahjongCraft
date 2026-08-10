package com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.IdentifiedTilePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import kotlinx.serialization.Serializable

/** [DiscardPile.DiscardEntry] 的共用 persistence DTO。 */
@Serializable
data class DiscardEntryPersistenceDto(
    val tile: IdentifiedTilePersistenceDto,
    val isTaken: Boolean,
)

/** [RiichiDiscardEntry] 的完整 persistence DTO。 */
@Serializable
data class RiichiDiscardEntryPersistenceDto(
    val tile: IdentifiedTilePersistenceDto,
    val isRiichi: Boolean,
    val isTaken: Boolean,
)

/** [RiichiDiscardPile] 的完整 persistence DTO。 */
@Serializable
data class RiichiDiscardPilePersistenceDto(val entries: List<RiichiDiscardEntryPersistenceDto>)

/** [TaiwanDiscardPile] 的完整 persistence DTO。 */
@Serializable
data class TaiwanDiscardPilePersistenceDto(val entries: List<DiscardEntryPersistenceDto>)

/** 建立已註冊內建日麻與台麻牌河的 persistence registry。 */
fun buildDiscardPilePersistenceRegistry(): PersistenceDtoRegistry<DiscardPile<*>> = PersistenceDtoRegistry<DiscardPile<*>>()
    .apply {
        register(
            typeKey = "builtin:riichi_discard_pile",
            domainClass = RiichiDiscardPile::class,
            serializer = RiichiDiscardPilePersistenceDto.serializer(),
            toDto = RiichiDiscardPile::toPersistenceDto,
            toDomain = RiichiDiscardPilePersistenceDto::toDomain,
        )
        register(
            typeKey = "builtin:taiwan_discard_pile",
            domainClass = TaiwanDiscardPile::class,
            serializer = TaiwanDiscardPilePersistenceDto.serializer(),
            toDto = TaiwanDiscardPile::toPersistenceDto,
            toDomain = TaiwanDiscardPilePersistenceDto::toDomain,
        )
    }

/** 將 [RiichiDiscardPile] 轉換成 persistence DTO。 */
private fun RiichiDiscardPile.toPersistenceDto(): RiichiDiscardPilePersistenceDto = RiichiDiscardPilePersistenceDto(
    entries.map { RiichiDiscardEntryPersistenceDto(it.tile.toPersistenceDto(), it.isRiichi, it.isTaken) },
)

/** 將日麻牌河 persistence DTO 還原成 [RiichiDiscardPile]。 */
private fun RiichiDiscardPilePersistenceDto.toDomain(): RiichiDiscardPile = entries.fold(RiichiDiscardPile()) { pile, entry ->
    pile.discard(RiichiDiscardEntry(entry.tile.toDomain(), entry.isRiichi, entry.isTaken))
}

/** 將 [TaiwanDiscardPile] 轉換成 persistence DTO。 */
private fun TaiwanDiscardPile.toPersistenceDto(): TaiwanDiscardPilePersistenceDto = TaiwanDiscardPilePersistenceDto(
    entries.map { DiscardEntryPersistenceDto(it.tile.toPersistenceDto(), it.isTaken) },
)

/** 將台麻牌河 persistence DTO 還原成 [TaiwanDiscardPile]。 */
private fun TaiwanDiscardPilePersistenceDto.toDomain(): TaiwanDiscardPile = entries.fold(TaiwanDiscardPile()) { pile, entry ->
    pile.discard(DiscardPile.DiscardEntry(entry.tile.toDomain(), entry.isTaken))
}
