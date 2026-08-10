package com.doublemoon1119.mahjongcraft.flow.persistence.dto.rule

import com.doublemoon1119.mahjongcraft.flow.persistence.dto.core.PersistenceDtoRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** [RiichiExhaustiveDrawReason] 的 persistence DTO。 */
@Serializable
data class RiichiExhaustiveDrawReasonPersistenceDto(val value: RiichiExhaustiveDrawReasonPersistenceValue)

/** [RiichiExhaustiveDrawReason] 種類的 persistence 值。 */
@Serializable
enum class RiichiExhaustiveDrawReasonPersistenceValue {
    NORMAL,
    KYUUSHU_KYUUHAI,
    SUUFON_RENDA,
    SUUKAN_NAGARE,
    SUUCHA_RIICHI,
    SANCHA_HOU,
}

/** 建立含內建規則流局原因的 persistence registry。 */
fun buildExhaustiveDrawReasonPersistenceRegistry(): PersistenceDtoRegistry<ExhaustiveDrawReason> = PersistenceDtoRegistry<ExhaustiveDrawReason>().apply {
    registerRiichiReason(
        typeKey = "riichi.exhaustive_draw.normal",
        domainClass = RiichiExhaustiveDrawReason.Normal::class,
        value = RiichiExhaustiveDrawReasonPersistenceValue.NORMAL,
        domain = RiichiExhaustiveDrawReason.Normal,
    )
    registerRiichiReason(
        typeKey = "riichi.exhaustive_draw.kyuushu_kyuuhai",
        domainClass = RiichiExhaustiveDrawReason.KyuushuKyuuhai::class,
        value = RiichiExhaustiveDrawReasonPersistenceValue.KYUUSHU_KYUUHAI,
        domain = RiichiExhaustiveDrawReason.KyuushuKyuuhai,
    )
    registerRiichiReason(
        typeKey = "riichi.exhaustive_draw.suufon_renda",
        domainClass = RiichiExhaustiveDrawReason.SuufonRenda::class,
        value = RiichiExhaustiveDrawReasonPersistenceValue.SUUFON_RENDA,
        domain = RiichiExhaustiveDrawReason.SuufonRenda,
    )
    registerRiichiReason(
        typeKey = "riichi.exhaustive_draw.suukan_nagare",
        domainClass = RiichiExhaustiveDrawReason.SuukanNagare::class,
        value = RiichiExhaustiveDrawReasonPersistenceValue.SUUKAN_NAGARE,
        domain = RiichiExhaustiveDrawReason.SuukanNagare,
    )
    registerRiichiReason(
        typeKey = "riichi.exhaustive_draw.suucha_riichi",
        domainClass = RiichiExhaustiveDrawReason.SuuchaRiichi::class,
        value = RiichiExhaustiveDrawReasonPersistenceValue.SUUCHA_RIICHI,
        domain = RiichiExhaustiveDrawReason.SuuchaRiichi,
    )
    registerRiichiReason(
        typeKey = "riichi.exhaustive_draw.sancha_hou",
        domainClass = RiichiExhaustiveDrawReason.SanchaHou::class,
        value = RiichiExhaustiveDrawReasonPersistenceValue.SANCHA_HOU,
        domain = RiichiExhaustiveDrawReason.SanchaHou,
    )
}

/** 將單一 [RiichiExhaustiveDrawReason] subtype 註冊到 persistence registry。 */
private fun <D : RiichiExhaustiveDrawReason> PersistenceDtoRegistry<ExhaustiveDrawReason>.registerRiichiReason(
    typeKey: String,
    domainClass: KClass<D>,
    value: RiichiExhaustiveDrawReasonPersistenceValue,
    domain: D,
) {
    register(
        typeKey = typeKey,
        domainClass = domainClass,
        serializer = RiichiExhaustiveDrawReasonPersistenceDto.serializer(),
        toDto = { RiichiExhaustiveDrawReasonPersistenceDto(value) },
        toDomain = { domain },
    )
}
