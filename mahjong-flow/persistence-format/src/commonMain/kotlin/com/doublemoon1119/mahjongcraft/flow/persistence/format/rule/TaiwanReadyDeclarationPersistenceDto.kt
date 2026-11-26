package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanReadyKind
import kotlinx.serialization.Serializable

/**
 * 台灣麻將宣告聽牌組件的 persistence DTO。
 *
 * @property kind 宣告種類。
 */
@Serializable
data class TaiwanReadyDeclarationPersistenceDto(
    val kind: TaiwanReadyKindPersistenceValue,
)

/** 宣告聽牌種類的穩定保存值。 */
@Serializable
enum class TaiwanReadyKindPersistenceValue {
    /** 一般宣告聽牌。 */
    GENERAL,

    /** 天聽。 */
    HEAVENLY,

    /** 地聽。 */
    EARTHLY,
}

/**
 * 轉成 persistence DTO。
 *
 * @return 轉換後的結果。
 */
fun TaiwanReadyKind.toPersistenceDto(): TaiwanReadyDeclarationPersistenceDto = TaiwanReadyDeclarationPersistenceDto(TaiwanReadyKindPersistenceValue.valueOf(name))

/**
 * 還原成宣告種類。
 *
 * @return 轉換後的結果。
 */
fun TaiwanReadyDeclarationPersistenceDto.toDomain(): TaiwanReadyKind = TaiwanReadyKind.valueOf(kind.name)
