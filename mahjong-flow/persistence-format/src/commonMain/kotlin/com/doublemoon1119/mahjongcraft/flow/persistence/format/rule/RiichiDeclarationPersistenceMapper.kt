package com.doublemoon1119.mahjongcraft.flow.persistence.format.rule

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDeclarationKind
import kotlin.uuid.Uuid

/**
 * 保存已成立的立直宣告。
 *
 * @return 宣告與牌河參照的保存資料。
 */
fun RiichiDeclaration.toPersistenceDto(): RiichiDeclarationPersistenceDto = RiichiDeclarationPersistenceDto(
    kind = kind.name,
    discardTileId = discardTileId.toString(),
    isIppatsu = isIppatsu,
)

/**
 * 驗證並還原已保存的立直宣告。
 *
 * @return 立直宣告；無法辨識的種類或識別碼會拒絕。
 */
fun RiichiDeclarationPersistenceDto.toDomain(): RiichiDeclaration = RiichiDeclaration(
    kind = RiichiDeclarationKind.valueOf(kind),
    discardTileId = Uuid.parse(discardTileId),
    isIppatsu = isIppatsu,
)
