package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.MeldType

/**
 * 內建捨牌反應的預設 policy。
 *
 * 這個 policy 只提供現有 Flow 能直接套用的 Pass、Ron、Chi、Pon 與明槓；第三方動作必須由規則模組
 * 明確提供對應效果，不能因為動作名稱相似就被當成內建鳴牌。
 */
object DefaultDiscardReactionPolicy : DiscardReactionPolicy {
    /**
     * 將合法的內建動作轉成反應候選。
     *
     * @param context 反應計算所需的桌況與合法動作。
     * @return 內建 Flow 可套用的反應候選。
     */
    override fun resolve(context: DiscardReactionPolicyContext): List<DiscardReactionCandidate> = context.legalActions.mapNotNull { action ->
        when (action) {
            GameAction.Pass -> DiscardReactionCandidate(action, priority = 0)
            is GameAction.Ron -> DiscardReactionCandidate(action, priority = 300)
            is GameAction.Chi -> claim(context, action, MeldType.CHI, priority = 100)
            is GameAction.Pon -> claim(context, action, MeldType.PON, priority = 200)
            is GameAction.Kan -> if (action.type == GameAction.KanType.OPEN_KAN) {
                claim(context, action, MeldType.OPEN_KAN, priority = 200)
            } else {
                null
            }

            else -> null
        }
    }

    /**
     * 由內建鳴牌動作建立取牌與公開效果。
     *
     * @param context 當前來源捨牌與反應者資料。
     * @param action 已指定來源牌與搭配手牌的內建動作。
     * @param meldType 成立後的副露種類。
     * @param priority 與其他反應比較的優先序，數值較大者優先。
     * @return 包含手牌選取、整組公開與補牌需求的候選。
     */
    private fun claim(
        context: DiscardReactionPolicyContext,
        action: GameAction,
        meldType: MeldType,
        priority: Int,
    ): DiscardReactionCandidate {
        val selectedTileIds = when (action) {
            is GameAction.Chi -> action.withTiles
            is GameAction.Pon -> action.withTiles
            is GameAction.Kan -> action.withTiles
            else -> error("Unsupported discard reaction claim action: $action")
        }
        return DiscardReactionCandidate(
            action = action,
            selectedTileIds = selectedTileIds,
            priority = priority,
            claimEffect = MeldClaimEffect(
                meldType = meldType,
                handTileIds = selectedTileIds,
                publiclyRevealedTileIds = (selectedTileIds + context.discardedTile.id).toSet(),
                faceDownTileIds = emptySet(),
                requiresSupplementalDraw = meldType == MeldType.OPEN_KAN,
            ),
        )
    }
}
