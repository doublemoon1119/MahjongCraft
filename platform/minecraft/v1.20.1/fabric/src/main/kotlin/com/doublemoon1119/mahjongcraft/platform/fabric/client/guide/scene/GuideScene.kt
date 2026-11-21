package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.guide.MinecraftGuideScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLean
import net.minecraft.block.BlockState
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import kotlin.uuid.Uuid

/**
 * 教學畫面中的一小塊示意場景：以方塊狀態與角色描述，交給遊戲自己的繪製器畫出。
 *
 * 場景座標與世界座標相同：一格等於一個方塊，Y 朝上；場景不放進任何世界，也不執行 tick。
 *
 * @property blocks 場景中的方塊。
 * @property figures 場景中的角色。
 * @property entities 場景中用原版繪製器畫出的生物，例如牌譜回放中的玩家與第三方角色。
 * @property tiles 場景中的麻將牌。
 * @property winEffects 場景中的胡牌特效。
 * @property view 觀看場景的斜上方視角。
 * @property focus 縮放、置中與顯示的範圍，範圍外的部分不顯示；`null` 時以所有方塊、角色與牌為準，整個場景都顯示。
 * @property frame 只決定縮放與置中的範圍，範圍外的部分照樣顯示，例如只框住牌桌而地板延伸到畫面外；
 * 有 [focus] 時不使用，兩者都沒有時以所有方塊、角色與牌為準。
 * @property rotatable 玩家能否用滑鼠拖曳轉動場景的水平方向。
 * @property roundInfo 浮在桌子中央上方的局況；沒有時為 null。
 * @property playerInfo 各座位後上方的玩家資訊；沒有時為 null。
 */
internal data class GuideScene(
    val blocks: List<GuideSceneBlock>,
    val figures: List<GuideSceneFigure>,
    val entities: List<GuideSceneEntity> = emptyList(),
    val tiles: List<GuideSceneTile>,
    val winEffects: List<GuideSceneWinEffect> = emptyList(),
    val view: GuideSceneView,
    val focus: GuideSceneBox? = null,
    val frame: GuideSceneBox? = null,
    val rotatable: Boolean = false,
    val roundInfo: GuideSceneRoundInfo? = null,
    val playerInfo: GuideScenePlayerInfo? = null,
)

/**
 * 場景中的一個方塊。
 *
 * @property state 要繪製的方塊狀態。
 * @property position 方塊所在的格子。
 */
internal data class GuideSceneBlock(
    val state: BlockState,
    val position: BlockPos,
)

/**
 * 場景中的一位麻將機器人。
 *
 * @property faceAssetKey 牌體正面的牌面資源鍵。
 * @property x 腳底中心的 X 座標。
 * @property y 腳底的 Y 座標。
 * @property z 腳底中心的 Z 座標。
 * @property yaw Minecraft 慣例的水平朝向（0 度朝南）。
 * @property seated 是否以坐姿繪製。
 * @property region 滑鼠指著時說明的區塊；不說明時為 null。
 * @property swingProgress 揮手進度，0 到 1；0 表示沒有揮手。
 * @property alpha 0 到 1 之間的不透明度；0 時不畫。
 */
internal data class GuideSceneFigure(
    val faceAssetKey: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val seated: Boolean,
    val region: GuideSceneRegion? = null,
    val swingProgress: Float = 0f,
    val alpha: Float = 1f,
)

/**
 * 場景中用原版繪製器畫出的一個實體。
 *
 * 實體不在任何世界中，由呼叫端建立與保留；坐姿時繪製器在繪製期間讓它騎在一個不在世界中的座位上，套用原版的坐姿，
 * 畫完即離開座位。
 *
 * @property entity 要畫的實體。
 * @property x 腳底中心的 X 座標。
 * @property y 腳底的 Y 座標。
 * @property z 腳底中心的 Z 座標。
 * @property yaw Minecraft 慣例的水平朝向（0 度朝南），身體與頭都朝這個方向。
 * @property seated 是否以坐姿繪製。
 * @property guard 包住這個實體繪製工作的隔離方式，例如第三方模型繪製失敗時改用備援；不需要時為 null。
 * @property swingProgress 生物主手的揮手進度，0 到 1；0 表示沒有揮手，不是生物時不使用。
 * @property alpha 0 到 1 之間的不透明度；0 時不畫。小於 1 時生物以原版半透明的方式畫出，不畫附加圖層；
 * 不是生物或繪製器不是原版生物繪製器時不畫。
 */
internal data class GuideSceneEntity(
    val entity: Entity,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val seated: Boolean,
    val guard: GuideSceneRenderGuard? = null,
    val swingProgress: Float = 0f,
    val alpha: Float = 1f,
)

/**
 * 場景中的一個胡牌特效，畫法與對局中打在胡牌張上的特效相同。
 *
 * @property x 胡牌張牌底中心的 X 座標。
 * @property y 胡牌張牌底的 Y 座標。
 * @property z 胡牌張牌底中心的 Z 座標。
 * @property elapsedTicks 從特效開始經過的 tick，可以有小數；不在特效長度內時不畫。
 * @property seed 決定三叉戟角度與閃電形狀的隨機種子。
 */
internal data class GuideSceneWinEffect(
    val x: Double,
    val y: Double,
    val z: Double,
    val elapsedTicks: Double,
    val seed: Long,
)

/** 包住場景中一個實體繪製工作的隔離方式。 */
internal fun interface GuideSceneRenderGuard {
    /**
     * 執行繪製工作，並依自己的規則處理失敗。
     *
     * @param draw 繪製工作。
     */
    fun run(draw: () -> Unit)
}

/**
 * 場景中的一張麻將牌，畫法與對局中的牌相同。
 *
 * @property assetKey 牌面資源鍵；看不到牌面（例如別人的手牌）時為 null。
 * @property x 牌底中心的 X 座標。
 * @property y 牌底的 Y 座標。
 * @property z 牌底中心的 Z 座標。
 * @property yaw Minecraft 慣例的水平朝向，與牌在桌上的擺放朝向相同。
 * @property pose 直立、牌面朝上或牌面朝下。
 * @property poseDegrees 動畫中介於兩個姿態之間的翻轉角度，取代 [pose] 的角度；不在動畫中時為 null。
 * @property lean 動畫中往前進方向的傾斜。
 * @property glow 外框發光的種類；`null` 時不發光。
 * @property region 滑鼠指著時說明的區塊；不說明時為 null。
 * @property key 呼叫端用來辨認滑鼠指著哪一張牌的識別值，例如局內牌索引；不需要辨認時為 null。
 */
internal data class GuideSceneTile(
    val assetKey: String?,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pose: MahjongTilePose = MahjongTilePose.STANDING,
    val poseDegrees: Float? = null,
    val lean: TileLean = TileLean.NONE,
    val glow: GuideSceneTileGlow? = null,
    val region: GuideSceneRegion? = null,
    val key: Int? = null,
)

/**
 * 滑鼠指著時會說明的牌桌區塊；同一區塊的牌一起畫上外框。
 *
 * @property nameKey 區塊名稱的翻譯鍵。
 * @property descriptionKey 區塊說明的翻譯鍵。
 */
internal enum class GuideSceneRegion(val nameKey: String, val descriptionKey: String) {
    /** 還沒被摸走的牌。 */
    WALL(MinecraftGuideScreenKeys.REGION_WALL, MinecraftGuideScreenKeys.REGION_WALL_DESCRIPTION),

    /** 自己的手牌。 */
    OWN_HAND(MinecraftGuideScreenKeys.REGION_OWN_HAND, MinecraftGuideScreenKeys.REGION_OWN_HAND_DESCRIPTION),

    /** 別人的手牌。 */
    OTHER_HAND(MinecraftGuideScreenKeys.REGION_OTHER_HAND, MinecraftGuideScreenKeys.REGION_OTHER_HAND_DESCRIPTION),

    /** 打出的牌。 */
    RIVER(MinecraftGuideScreenKeys.REGION_RIVER, MinecraftGuideScreenKeys.REGION_RIVER_DESCRIPTION),

    /** 吃、碰、槓亮出的牌組。 */
    MELD(MinecraftGuideScreenKeys.REGION_MELD, MinecraftGuideScreenKeys.REGION_MELD_DESCRIPTION),

    /** 桌子中央上方的局況。 */
    ROUND_INFO(MinecraftGuideScreenKeys.REGION_ROUND_INFO, MinecraftGuideScreenKeys.REGION_ROUND_INFO_DESCRIPTION),

    /** 各座位後上方的玩家資訊。 */
    PLAYER_INFO(MinecraftGuideScreenKeys.REGION_PLAYER_INFO, MinecraftGuideScreenKeys.REGION_PLAYER_INFO_DESCRIPTION),

    /** 坐在座位上的 AI。 */
    AI_PLAYER(MinecraftGuideScreenKeys.REGION_AI_PLAYER, MinecraftGuideScreenKeys.REGION_AI_PLAYER_DESCRIPTION),
}

/**
 * 浮在桌子中央上方的局況，交給遊戲的局況繪製器畫出。
 *
 * @property lines 局況的資訊行。
 * @property x 局況中心的 X 座標。
 * @property y 局況中心的 Y 座標。
 * @property z 局況中心的 Z 座標。
 */
internal data class GuideSceneRoundInfo(
    val lines: List<RoundInfoLine>,
    val x: Double,
    val y: Double,
    val z: Double,
)

/**
 * 各座位後上方的玩家資訊，交給遊戲的玩家資訊繪製器畫出。
 *
 * @property players 各座位的公開資訊。
 * @property dealerPlayerId 莊家；沒有時為 null。
 * @property shownSeats 要畫出面板的座位 index；鏡頭所在座位的面板會擋住視線，不畫。
 * @property x 桌子中心的 X 座標。
 * @property y 桌子底部的 Y 座標。
 * @property z 桌子中心的 Z 座標。
 */
internal data class GuideScenePlayerInfo(
    val players: List<MahjongPlayerInfoEntry>,
    val dealerPlayerId: Uuid?,
    val shownSeats: Set<Int>,
    val x: Double,
    val y: Double,
    val z: Double,
)

/** 對局中麻將牌外框發光的種類。 */
internal enum class GuideSceneTileGlow {
    /** 準星指著的牌。 */
    TARGET,

    /** 與準星指著的牌相同的其他牌。 */
    MATCHING,

    /** 最後打出、仍留在牌河的牌。 */
    LATEST_DISCARD,
}

/**
 * 場景座標中的長方體範圍。
 *
 * @property minX 最小 X。
 * @property minY 最小 Y。
 * @property minZ 最小 Z。
 * @property maxX 最大 X。
 * @property maxY 最大 Y。
 * @property maxZ 最大 Z。
 */
internal data class GuideSceneBox(
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
    val maxZ: Double,
)

/**
 * 從斜上方觀看場景的固定視角；縮放由畫面可用空間決定。
 *
 * @property yawDegrees 場景繞垂直軸旋轉的角度。
 * @property pitchDegrees 鏡頭往下俯視的角度。
 */
internal data class GuideSceneView(
    val yawDegrees: Float,
    val pitchDegrees: Float,
)

/**
 * 滑鼠指著的東西：所屬的區塊與它在畫面上的範圍，供提示框避開。
 *
 * @property region 所屬的區塊。
 * @property left 左界。
 * @property top 上界。
 * @property right 右界。
 * @property bottom 下界。
 */
internal data class GuideSceneHover(
    val region: GuideSceneRegion,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
