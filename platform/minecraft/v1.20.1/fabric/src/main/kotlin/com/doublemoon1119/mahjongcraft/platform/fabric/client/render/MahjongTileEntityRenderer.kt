package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileEntityRenderer.Companion.LABEL_MARGIN_RATIO
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileEntityRenderer.Companion.LABEL_SCALE
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_TILE_ASSET_KEYS
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelText
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileMotionAnimation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileMotionAnimationSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.item.ItemRenderer
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import kotlin.uuid.toKotlinUuid

/**
 * 使用既有麻將牌 item model 呈現牌張的 client renderer；自由放置與牌局管理中的牌共用同一個 entity
 * 類型，牌面來源不同：自由放置直接讀 entity 自身 tracked data，牌局管理中的牌改依 entity UUID
 * （等同 `IdentifiedTile.id`）查詢 [stateStore] 目前收到的可見性快照，完全不看 entity 自身的
 * `tileAssetKey`（該欄位在管理模式下恆為 [UNKNOWN_TILE_ASSET_KEY]，見 [MahjongTileEntity]）。
 *
 * 牌面角落輔助標籤（[TileLabelRegistry]，給非中文圈玩家看的數字／字母）在 [clientConfigStore] 開啟時
 * 額外疊加繪製，見 [renderCornerLabels]。
 *
 * 特殊視覺強調（例如日麻寶牌發光，見 [MahjongRuleModule.isHighlightedTile]）也是逐幀在這裡由
 * client 端自行判斷，見 [isHighlighted]——理由跟牌面解析完全一致：這個判斷只能在真正解析出可見牌面
 * 之後才進行，對觀察者看不到牌面的隱藏牌天然不會觸發，不需要另外處理保密。
 */
class MahjongTileEntityRenderer(
    context: EntityRendererFactory.Context,
    private val stateStore: ClientMahjongStateStore,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val tileLabelRegistry: TileLabelRegistry,
    private val clientConfigStore: MahjongClientConfigStore,
    private val moduleRegistry: MahjongModuleRegistry,
) : EntityRenderer<MahjongTileEntity>(context) {
    /** 共用 Vanilla item renderer，避免建立第二套牌面模型格式。 */
    private val itemRenderer = context.itemRenderer

    /** 共用 Vanilla text renderer，繪製角落輔助標籤用。 */
    private val textRenderer = context.textRenderer

    /** 依 entity yaw 與姿態旋轉模型，並補償原點使牌底貼齊所在表面。 */
    override fun render(
        entity: MahjongTileEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val elapsedAnimationTicks = entity.world.time.toDouble() + tickDelta - entity.animationStartGameTime
        // 動畫的 startGameTime 是「這張牌該開始掉落」的時間點（牌牆生成掉落波浪用每墩不同的 stagger
        // 延遲，見 FabricMahjongTileWallPresenter.startWallDropAnimations），在那之前 elapsed 是負值——
        // 這段時間這張牌根本不該出現在畫面上（設計上要隱形／延遲生成，而不是提早出現在半空定格
        // 不動），直接整個跳過繪製，比在 frame() 裡把 progress 硬夾到 0 更符合預期。
        if (entity.animating && elapsedAnimationTicks < 0.0) return
        val animationFrame = if (entity.animating) {
            TileMotionAnimation(
                TileMotionAnimationSpec(
                    durationTicks = entity.animationDurationTicks,
                    arcHeight = entity.animationArcHeight,
                    startPoseRotationDegrees = entity.animationStartPoseRotationDegrees,
                    endPoseRotationDegrees = entity.animationEndPoseRotationDegrees,
                ),
            ).frame(elapsedTicks = elapsedAnimationTicks, startOffset = entity.animationStartOffset)
        } else {
            null
        }
        matrices.push()
        if (animationFrame != null) matrices.translate(animationFrame.offset.x, animationFrame.offset.y, animationFrame.offset.z)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.yaw + 180.0f))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(animationFrame?.poseRotationDegrees ?: entity.tilePose.rotationDegrees))
        when (entity.tilePose) {
            MahjongTilePose.STANDING -> matrices.translate(0.0, MahjongTileEntity.TILE_HEIGHT / 2.0, 0.0)
            MahjongTilePose.FACE_UP -> matrices.translate(0.0, 0.0, -MahjongTileEntity.TILE_DEPTH / 2.0)
            MahjongTilePose.FACE_DOWN -> matrices.translate(0.0, 0.0, MahjongTileEntity.TILE_DEPTH / 2.0)
        }
        val assetKey = entity.resolvedTileAssetKey()
        val highlighted = entity.isHighlighted()
        val stack = tileStacks[assetKey] ?: tileStacks.getValue(UNKNOWN_TILE_ASSET_KEY)
        val consumers = if (highlighted) vertexConsumers.withGlint() else vertexConsumers
        itemRenderer.renderItem(stack, ModelTransformationMode.HEAD, light, OverlayTexture.DEFAULT_UV, matrices, consumers, entity.world, entity.id)
        if (highlighted) {
            // 遊戲內驗證時發現單層光暈疊圖不夠明顯，額外疊一次「只有光暈、不含正常牌面」的
            // pass，讓同一個模型的光暈幾何再疊加一次（GLINT_TRANSPARENCY 是相加混合，疊兩次亮度會
            // 明顯提升），比單純換 solid 參數（改變的是光暈貼圖本身的縮放／滾動速度，不是強度）更
            // 直接有效，且不影響正常牌面只畫一次、不會有額外的不透明面覆寫。
            itemRenderer.renderItem(
                stack,
                ModelTransformationMode.HEAD,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertexConsumers.glintOnly(),
                entity.world,
                entity.id,
            )
        }
        renderCornerLabels(assetKey, matrices, vertexConsumers, light)
        matrices.pop()
    }

    /**
     * 疊加繪製牌面角落輔助標籤；只在這個 asset key 有註冊標籤，且（[MahjongClientConfigStore.current]
     * 開啟，或標籤本身標記 [TileLabel.forced] 強制顯示——
     * 例如八張花牌彼此外觀相近，標籤兼有辨識花色與順序的功能，不只是給非中文圈玩家看的輔助資訊，因此
     * 無視玩家本機開關）時才畫。呼叫時機在 [itemRenderer] 畫完牌面之後、`matrices.pop()` 之前，沿用
     * 同一個已經套用
     * yaw／姿態旋轉與位移的 [matrices] 狀態——`itemRenderer.renderItem` 內部的牌面模型固定以這個狀態
     * 的原點為中心，向 X／Y 方向各展開半個 [MahjongTileEntity.TILE_WIDTH]／[MahjongTileEntity.TILE_HEIGHT]、
     * 印刷面朝向本地 -Z（半個 [MahjongTileEntity.TILE_DEPTH]；四邊形要正確朝向鏡頭還需要額外處理，見
     * [drawCornerLabel] KDoc）——這個關係不受姿態影響（三種姿態呼叫 `renderItem` 前各自的位移已經把
     * 姿態差異吸收掉了，模型本身相對這個已位移原點的內部幾何固定不變），所以角落偏移量可以直接用這
     * 三個常數算，不需要依姿態另外分支。
     *
     * 角落座標系是模型自己的本地座標，不是世界座標：對玩家來說「左上」「右上」是指這張牌本身印刷面的
     * 左上／右上角，跟牌實際被姿態／yaw 轉到世界哪個方向無關——這正是希望的效果（不管牌被轉到哪個
     * 方向，標籤永遠貼在同一個印刷面角落）。
     */
    private fun renderCornerLabels(
        assetKey: String,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        val label = tileLabelRegistry.find(assetKey) ?: return
        if (!clientConfigStore.current.tileLabelsEnabled && !label.forced) return
        label.topLeft?.let { drawCornerLabel(it, isLeft = true, matrices, vertexConsumers, light) }
        label.topRight?.let { drawCornerLabel(it, isLeft = false, matrices, vertexConsumers, light) }
    }

    /**
     * 畫單一角落的標籤文字：水平方向靠左／右邊緣留 [LABEL_MARGIN_RATIO] 比例的留白，垂直方向固定貼齊
     * 上緣。[LABEL_SCALE]／[LABEL_MARGIN_RATIO] 都是遊戲內截圖比對調整過的數值。
     *
     * Z 貼齊印刷面（局部 -Z，[MahjongTileEntity.TILE_DEPTH] 的一半），不額外往外推——原本留了一個小
     * 位移避免跟牌面材質 z-fighting，遊戲內驗證過用 [TextRenderer.TextLayerType.POLYGON_OFFSET] 這個
     * layer type 就足夠避免 z-fighting，不需要再額外位移，直接貼合印刷面即可。
     *
     * 印刷面法線在這個本地座標系是朝向局部 -Z（跟 [MahjongTileEntityRenderer] 類別 KDoc 原本假設的
     * 模型幾何一致），但單純把文字四邊形放在 -Z 位置並不會自動朝向鏡頭——[TextRenderer.draw] 建立的
     * 四邊形有固定的環繞方向（winding），套用一般的 Y 軸負縮放（讓文字從 2D「y 軸向下」慣例翻成這裡
     * 「y 軸向上」）並不會一併翻轉四邊形朝向鏡頭的那一面，所以額外用 [RotationAxis.POSITIVE_Y] 轉
     * 180 度翻轉四邊形朝向（純旋轉，不是鏡射，文字仍然正常可讀，不會變成鏡像字）——這一步跟該取
     * 哪一側 Z 座標是兩個獨立的問題，遊戲內截圖都驗證過。水平方向的左右角落也跟著這個 180 度旋轉
     * 對調（[isLeft] 為 true 時反而要放在 `+X`，不是直覺上的 `-X`），同樣是遊戲內驗證過的結果。
     *
     * [TextRenderer.draw] 這個多載直接吃 [MatrixStack.peek] 的 position matrix，讓文字跟著目前已經
     * 套用的旋轉／位移狀態走，不會是永遠面向鏡頭的 billboard 文字——文字必須固定印在牌面上，不能因為
     * 鏡頭角度改變而跟著轉向。
     */
    private fun drawCornerLabel(
        text: TileLabelText,
        isLeft: Boolean,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        matrices.push()
        val halfWidth = MahjongTileEntity.TILE_WIDTH / 2.0
        val halfHeight = MahjongTileEntity.TILE_HEIGHT / 2.0
        val marginX = MahjongTileEntity.TILE_WIDTH * LABEL_MARGIN_RATIO
        val marginY = MahjongTileEntity.TILE_HEIGHT * LABEL_MARGIN_RATIO
        val x = if (isLeft) halfWidth - marginX else -halfWidth + marginX
        val y = halfHeight - marginY
        val z = -(MahjongTileEntity.TILE_DEPTH / 2.0)
        matrices.translate(x, y, z)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f))
        val scale = LABEL_SCALE.toFloat()
        matrices.scale(scale, -scale, scale)
        val width = textRenderer.getWidth(text.text)
        val originX = if (isLeft) 0.0f else -width.toFloat()
        textRenderer.draw(
            text.text,
            originX,
            0.0f,
            text.color.toArgb(),
            false,
            matrices.peek().positionMatrix,
            vertexConsumers,
            TextRenderer.TextLayerType.POLYGON_OFFSET,
            0,
            light,
        )
        matrices.pop()
    }

    /** ItemRenderer 自行解析材質，因此 entity renderer 不提供單一 texture。 */
    override fun getTexture(entity: MahjongTileEntity): Identifier? = null

    /**
     * 自由放置牌沿用 entity 自身 tracked data；牌局管理中的牌改查 [stateStore]——查不到（不在目前
     * 對局範圍）或未對目前觀察者揭露時顯示牌背，查得到就換算成對應正面 asset key。
     */
    private fun MahjongTileEntity.resolvedTileAssetKey(): String {
        if (!managedByGame) return tileAssetKey
        val tile = stateStore.findManagedTileSnapshot(uuid.toKotlinUuid())?.tile ?: return UNKNOWN_TILE_ASSET_KEY
        return tile.toAssetKey(tileAssetRegistry)
    }

    /**
     * 這張管理中的牌對目前觀察者而言，牌面是否已解析出來、且該有特殊視覺強調
     * （[MahjongRuleModule.isHighlightedTile]，例如日麻寶牌）。自由放置的牌、或牌面對目前觀察者
     * 不可見（[stateStore] 查無對應快照）的管理中牌，一律回傳 `false`——不需要另外判斷保密，理由見
     * 類別 KDoc。
     */
    private fun MahjongTileEntity.isHighlighted(): Boolean {
        if (!managedByGame) return false
        val tile = stateStore.findManagedTileSnapshot(uuid.toKotlinUuid())?.tile ?: return false
        val snapshot = stateStore.gameSnapshot ?: return false
        val module = moduleRegistry.getModule(snapshot.config)
        val revealedWallTiles = snapshot.tileWall.tiles.mapNotNull { it.tile }
        return module.isHighlightedTile(tile, revealedWallTiles)
    }

    /**
     * 疊加附魔物品光暈疊圖層——用 [ItemRenderer.getDirectItemGlintConsumer]（entity 世界渲染用的
     * direct 版本，不是 GUI 用的 [ItemRenderer.getItemGlintConsumer]）包一層，讓
     * [itemRenderer]（`solid` 參數暫定 `false`，遊戲內驗證光暈貼合效果後可再調整）在同一次
     * `renderItem` 呼叫裡連同正常牌面一起疊畫，完全不用碰 [ItemStack] 本身的附魔資料
     * （見 [MahjongTileEntityRenderer] 類別 KDoc：`tileStacks` 是全部 entity 共用的靜態實例，不能
     * 每幀個別修改）也不用 `Entity.setGlowing()`（保留給其他用途）。
     */
    private fun VertexConsumerProvider.withGlint(): VertexConsumerProvider = VertexConsumerProvider { layer -> ItemRenderer.getDirectItemGlintConsumer(this, layer, false, true) }

    /**
     * 純光暈疊圖層，不含正常牌面——所有 buffer 請求一律導向
     * [RenderLayer.getDirectEntityGlint]，忽略呼叫端原本要求的 layer（正常牌面已經在第一次
     * `renderItem` 呼叫時畫過一次），用來在 [render] 額外疊加第二次光暈幾何、加強亮度，見 [render]
     * 內的呼叫點註解。
     */
    private fun VertexConsumerProvider.glintOnly(): VertexConsumerProvider = VertexConsumerProvider { _ -> this.getBuffer(RenderLayer.getDirectEntityGlint()) }

    companion object {
        /** 每個合法 asset key 共用一個只供渲染使用的 ItemStack。 */
        private val tileStacks: Map<String, ItemStack> = ALL_TILE_ASSET_KEYS.associateWith { assetKey ->
            ItemStack(ModItems.MAHJONG_TILE).also { MahjongTileItem.writeTileAssetKey(it, assetKey) }
        }

        /** 角落標籤水平／垂直留白相對牌本身寬／高的比例，遊戲內驗證後從初始估算值調小、貼近角落。 */
        private const val LABEL_MARGIN_RATIO: Double = 0.06

        /** 角落標籤文字縮放；[TextRenderer] 預設字高約 9px，遊戲內驗證後從初始估算值調小。 */
        private const val LABEL_SCALE: Double = 0.004
    }
}

/** 將標籤顏色語意值轉成 [TextRenderer.draw] 需要的不透明 ARGB 色碼。 */
private fun TileLabelColor.toArgb(): Int = when (this) {
    TileLabelColor.BLACK -> 0xFF000000.toInt()
    TileLabelColor.RED -> 0xFFFF0000.toInt()
}
