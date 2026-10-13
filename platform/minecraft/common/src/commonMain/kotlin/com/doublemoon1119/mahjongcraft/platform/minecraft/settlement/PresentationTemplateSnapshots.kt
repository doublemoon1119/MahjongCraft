package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 建立獨立的宣告式布局快照，避免呼叫端修改已登記模板。
 *
 * @receiver 要複製的布局樹。
 * @return 不共用可變子集合的布局快照。
 */
internal fun PresentationLayout.snapshot(): PresentationLayout = when (this) {
    is PresentationLayout.Text -> copy()
    is PresentationLayout.PlayerIdentity -> copy()
    is PresentationLayout.Tile -> copy()
    is PresentationLayout.TileList -> copy()
    is PresentationLayout.TileGroups -> copy()
    is PresentationLayout.RepeatEntries -> copy()
    is PresentationLayout.Row -> copy(children = children.map(PresentationLayout::snapshot))
    is PresentationLayout.Column -> copy(children = children.map(PresentationLayout::snapshot))
    is PresentationLayout.Weighted -> copy(child = child.snapshot())
    is PresentationLayout.Grid -> copy(children = children.map(PresentationLayout::snapshot))
    is PresentationLayout.Spacer -> copy()
    is PresentationLayout.SizeConstraint -> copy(child = child.snapshot())
    is PresentationLayout.Box -> copy(children = children.map { it.snapshot() as PresentationLayout.Positioned })
    is PresentationLayout.Positioned -> copy(child = child.snapshot())
    is PresentationLayout.IfPresent -> copy(child = child.snapshot())
    is PresentationLayout.Animated -> copy(
        child = child.snapshot(),
        effects = effects.map(PresentationAnimationEffect::snapshot),
    )
}

/**
 * 建立動畫效果的獨立快照。
 *
 * @receiver 要複製的動畫效果。
 * @return 不共用關鍵影格清單的動畫效果快照。
 */
private fun PresentationAnimationEffect.snapshot(): PresentationAnimationEffect = when (this) {
    is PresentationAnimationEffect.Fade -> copy()
    is PresentationAnimationEffect.Slide -> copy()
    is PresentationAnimationEffect.ScaleKeyframes -> copy(keyframes = keyframes.toList())
    is PresentationAnimationEffect.BackgroundPulse -> copy()
    is PresentationAnimationEffect.HighlightSweep -> copy()
    is PresentationAnimationEffect.HorizontalReveal -> copy()
}

/**
 * 建立揭示序列的獨立快照。
 *
 * @receiver 要複製的揭示序列。
 * @return 只複製可變聲音清單的揭示序列快照。
 */
internal fun WinSettlementRevealSequence.snapshot(): WinSettlementRevealSequence = copy(sounds = sounds.toList())

/**
 * 建立胡牌結算模板的獨立快照。
 *
 * @receiver 要複製的胡牌結算模板。
 * @return 不共用布局樹、揭示音效清單或詳情標籤 map 的模板快照。
 */
internal fun WinSettlementPresentationTemplate.snapshot(): WinSettlementPresentationTemplate = copy(
    root = root.snapshot(),
    reveal = reveal.snapshot(),
    detailFieldLabelKeys = detailFieldLabelKeys.toMap(),
)

/**
 * 建立終局結算模板快照。
 *
 * @receiver 要複製的終局結算模板。
 * @return 該模板的獨立值快照；其欄位全為不可變 scalar 或 enum，不需額外深複製。
 */
internal fun MatchSettlementPresentationTemplate.snapshot(): MatchSettlementPresentationTemplate = copy()
