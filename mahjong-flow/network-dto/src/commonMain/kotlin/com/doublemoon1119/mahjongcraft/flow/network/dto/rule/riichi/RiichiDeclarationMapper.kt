package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDeclarationKind
import kotlin.uuid.Uuid

/**
 * 轉換已公開的立直宣告。
 *
 * @return 只含宣告種類、捨牌參照及一發資格的網路資料。
 */
fun RiichiDeclaration.toDto(): RiichiDeclarationViewDto = RiichiDeclarationViewDto(
    kind = kind.name,
    discardTileId = discardTileId.toString(),
    isIppatsu = isIppatsu,
)

/**
 * 驗證並還原公開的立直宣告。
 *
 * @return 具型別的宣告資料。
 */
fun RiichiDeclarationViewDto.toDomain(): RiichiDeclaration = RiichiDeclaration(
    kind = RiichiDeclarationKind.valueOf(kind),
    discardTileId = Uuid.parse(discardTileId),
    isIppatsu = isIppatsu,
)
