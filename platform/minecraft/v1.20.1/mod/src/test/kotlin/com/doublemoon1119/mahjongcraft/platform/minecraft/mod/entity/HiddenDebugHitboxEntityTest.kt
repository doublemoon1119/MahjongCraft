package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.HiddenDebugHitboxEntity
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證碰撞箱除錯顯示只略過不能點擊的面板。 */
class HiddenDebugHitboxEntityTest {
    /** 資訊面板與結算面板不畫碰撞箱。 */
    @Test
    fun `display panels hide their debug hitbox`() {
        listOf(
            MahjongRoundInfoEntity::class,
            MahjongLobbyInfoEntity::class,
            MahjongPlayerInfoEntity::class,
            WinSettlementPresentationEntity::class,
            ExhaustiveDrawSettlementPresentationEntity::class,
            MatchSettlementPresentationEntity::class,
        ).forEach { assertTrue(it.hidesDebugHitbox(), "${it.simpleName} should hide its debug hitbox") }
    }

    /** 可以點擊或互動的實體保留碰撞箱，讓除錯顯示看得出點擊範圍。 */
    @Test
    fun `interactive entities keep their debug hitbox`() {
        listOf(
            MahjongTileEntity::class,
            MahjongTileSelectionConfirmEntity::class,
            MahjongDiceEntity::class,
            MahjongScoringStickEntity::class,
            MahjongRulerEntity::class,
            MahjongBotEntity::class,
        ).forEach { assertFalse(it.hidesDebugHitbox(), "${it.simpleName} should keep its debug hitbox") }
    }

    /**
     * 判斷實體類別是否在除錯顯示中略過碰撞箱。
     *
     * @receiver 實體類別。
     * @return 略過時為 true。
     */
    private fun KClass<*>.hidesDebugHitbox(): Boolean = HiddenDebugHitboxEntity::class.java.isAssignableFrom(java)
}
