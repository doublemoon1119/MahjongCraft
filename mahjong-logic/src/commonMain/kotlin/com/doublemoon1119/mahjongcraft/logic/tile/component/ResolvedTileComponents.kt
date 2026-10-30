package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 合併後的權威牌張描述，只能再經讀取情境裁切，不直接傳給觀察者。
 *
 * @property area 目前牌張與所在區域。
 * @property components 已驗證並按來源層級合併的組件。
 * @property publicEvidence 是否已有全桌公開的明確證據。
 */
data class ResolvedTileComponents(
    val area: TileAreaEntry,
    val components: ComponentSet,
    val publicEvidence: Boolean,
) {
    /** 規則要求的共同姿態，與讀取者無關。 */
    val pose: TilePose get() = checkNotNull(components[BuiltInTileComponents.POSE])

    /** 此牌是否已經對公共讀取者公開。 */
    val isPublic: Boolean get() = publicEvidence || components[BuiltInTileComponents.FACE_ACCESS] == TileFaceAccess.Public

    /**
     * 投影可展示的牌面與特徵；牌面不可讀時連特徵身分也不輸出。
     *
     * @param context 已通過入口授權的讀取情境。
     * @param ruleModuleId 產生描述的規則識別碼。
     * @param tableState 產生描述時的同一份桌況。
     * @param registry 本次執行的特徵投影定義。
     * @return 已裁切且不帶局內身分的牌面；無權讀取時為 null。
     */
    fun faceViewFor(
        context: TileReadContext,
        ruleModuleId: String,
        tableState: TableState,
        registry: ComponentRegistry,
    ): TileFaceView? {
        if (!canRead(context)) return null
        val reader = when (context) {
            is TileReadContext.Player -> ComponentReadContext.Player(context.playerId)
            is TileReadContext.Spectator -> ComponentReadContext.Spectator(context.observerId)
            is TileReadContext.Public -> ComponentReadContext.Public
        }
        val views = registry.project(
            context = ComponentProjectionContext(
                ruleModuleId = ruleModuleId,
                tableState = tableState,
                ownerType = BuiltInComponentOwners.TILE,
                owner = area.tile,
                reader = reader,
            ),
            components = components,
        )
        return TileFaceView(
            tile = area.tile.tile,
            views = views,
            appearanceId = appearanceFor(context),
        )
    }

    /**
     * 判斷讀取者是否獲准知道這張牌，不由姿態推測權限。
     *
     * @param context 已通過入口檢查的讀取情境。
     * @return 有明確權限時為 true。
     */
    fun canRead(context: TileReadContext): Boolean {
        if (isPublic) return true
        val handArea = area.region == TileRegion.HAND || area.region == TileRegion.DRAWN || area.region == TileRegion.MELD
        return when (context) {
            is TileReadContext.Player ->
                area.ownerId == context.playerId ||
                    handArea &&
                    area.ownerId in context.publicHandPlayerIds ||
                    (components[BuiltInTileComponents.FACE_ACCESS] as? TileFaceAccess.Players)?.playerIds?.contains(context.playerId) == true
            is TileReadContext.Spectator -> handArea && area.ownerId in context.visibleHandPlayerIds
            is TileReadContext.Public -> handArea && area.ownerId in context.publicHandPlayerIds
        }
    }

    /**
     * 只為已知牌面回傳可使用的外觀樣式，不把私有組件值交給未知牌讀取者。
     *
     * @param context 已通過入口檢查的讀取情境。
     * @return 可使用的樣式 ID；未知牌或未設定時為 null。
     */
    fun appearanceFor(context: TileReadContext): String? = components[BuiltInTileComponents.APPEARANCE].takeIf { canRead(context) }
}
