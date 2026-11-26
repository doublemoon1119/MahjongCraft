package com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan

import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanReadyKind

/**
 * 轉成公開宣告聽牌的 DTO。
 *
 * @return 宣告聽牌 DTO。
 */
fun TaiwanReadyKind.toDto(): TaiwanReadyDeclarationViewDto = TaiwanReadyDeclarationViewDto(kind = name)

/**
 * 還原成宣告聽牌種類。
 *
 * @return 宣告聽牌種類。
 */
fun TaiwanReadyDeclarationViewDto.toDomain(): TaiwanReadyKind = TaiwanReadyKind.valueOf(kind)
