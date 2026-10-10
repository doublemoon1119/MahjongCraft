package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * [MahjongPlayerAreaPresenter.presentWinCelebration] 的處理結果。
 *
 * @property result 跟 [MahjongPlayerAreaPresenter.present] 同一組結果分類（找不到桌子／有牌找不到既有
 * entity）。
 * @property handLaydownEndGameTime [MahjongWinCelebrationPresentation.organizedStandingTileIds] 全部
 * 一起倒下播完的絕對 game time；[result] 不是 [MahjongPlayerAreaPresentationResult.PRESENTED] 時為
 * `null`，呼叫端不應該再接續排定特效。
 */
data class MahjongWinCelebrationResult(
    val result: MahjongPlayerAreaPresentationResult,
    val handLaydownEndGameTime: Long?,
)
