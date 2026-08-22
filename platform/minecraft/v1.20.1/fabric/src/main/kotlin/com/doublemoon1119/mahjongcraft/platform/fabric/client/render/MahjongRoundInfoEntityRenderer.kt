package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * 桌面中央局況顯示的 client renderer——手動用 [TextRenderer] 逐行畫字，不是 vanilla
 * `TextDisplayEntity` 那一整套設定（billboard mode／背景色板等欄位在 1.20.1 有一部分是
 * protected/NBT-only，直接沿用 `EntityRenderer.renderLabelIfPresent`（vanilla 生物名牌）的 billboard
 * 手法風險更低）。
 *
 * 已用 `javap -c` 對照 Yarn 1.20.1 mapping 反組譯 `EntityRenderer.renderLabelIfPresent` 位元碼確認
 * vanilla 的確切做法：[dispatcher] 的攝影機旋轉（`getRotation()`）套用到矩陣、縮放用
 * `(-scale, -scale, scale)`（X 軸刻意是負的，不是正的——名牌文字本身是左右鏡射過的座標系）、
 * `TextRenderer.draw` 用 [TextRenderer.TextLayerType.NORMAL]（不是 `POLYGON_OFFSET`，那是給貼在其他
 * 已渲染表面上的疊字用的，跟這裡懸浮在半空中的文字情境不同）。
 *
 * 兩行文字都在這裡（client 端、每一幀）才用 [Text.translatable] 組出來，不是拿 server 端已經翻譯好的
 * 固定字串——[MahjongRoundInfoEntity] 只同步場風／局數／本場數／牌山剩餘這些原始數值，讓每個玩家依
 * 自己的語系看到對應翻譯，見該 entity KDoc。
 */
class MahjongRoundInfoEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<MahjongRoundInfoEntity>(context) {
    /** 共用 Vanilla text renderer，畫法比照 [MahjongTileEntityRenderer]。 */
    private val textRenderer = context.textRenderer

    override fun render(
        entity: MahjongRoundInfoEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val lines = listOf(entity.buildTitleText(), entity.buildWallRemainingText()) + entity.buildExtraTexts()

        matrices.push()
        matrices.multiply(dispatcher.rotation)
        matrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE)
        val lineHeight = textRenderer.fontHeight + LINE_SPACING
        val totalHeight = lineHeight * lines.size
        lines.forEachIndexed { index, line ->
            val width = textRenderer.getWidth(line)
            val x = -width / 2.0f
            val y = -totalHeight / 2.0f + index * lineHeight
            textRenderer.draw(
                line,
                x,
                y,
                TEXT_COLOR,
                false,
                matrices.peek().positionMatrix,
                vertexConsumers,
                TextRenderer.TextLayerType.NORMAL,
                BACKGROUND_COLOR,
                light,
            )
        }
        matrices.pop()
    }

    /** 標題行：場風＋場風內局數＋本場數，例如「東1局 0本場」。 */
    private fun MahjongRoundInfoEntity.buildTitleText(): Text = Text.translatable(
        MinecraftMessageKeys.ROUND_INFO_TITLE,
        Text.translatable(prevalentWind.toMessageKey()),
        localRoundNumber,
    ).append(" ").append(Text.translatable(MinecraftMessageKeys.ROUND_INFO_COMBO_COUNT, comboCount))

    /** 牌山剩餘行。 */
    private fun MahjongRoundInfoEntity.buildWallRemainingText(): Text = Text.translatable(MinecraftMessageKeys.ROUND_INFO_WALL_REMAINING, wallRemainingCount)

    /**
     * 規則自訂延伸顯示行——只有認得的 key 才產生對應文字，其餘略過（forward-compatible），比照
     * `GameActionDisplayText.exhaustiveDrawText()` 對 `RiichiExhaustiveDrawReason` 的既有型別/key
     * 判斷慣例。
     */
    private fun MahjongRoundInfoEntity.buildExtraTexts(): List<Text> = extras.mapNotNull { extra ->
        when (extra.key) {
            RiichiRuleModule.RIICHI_STICK_POT_KEY -> Text.translatable(MinecraftMessageKeys.ROUND_INFO_RIICHI_STICK_POT, extra.value)
            else -> null
        }
    }

    /** 場風對應的牌面顯示文字 key，跟手牌裡的風牌共用同一組翻譯（同一個詞，不需要另外一套 key）。 */
    private fun Wind.toMessageKey(): String = when (this) {
        Wind.EAST -> MinecraftMessageKeys.TILE_HONOR_EAST
        Wind.SOUTH -> MinecraftMessageKeys.TILE_HONOR_SOUTH
        Wind.WEST -> MinecraftMessageKeys.TILE_HONOR_WEST
        Wind.NORTH -> MinecraftMessageKeys.TILE_HONOR_NORTH
    }

    /** 純文字顯示，不提供單一 texture。 */
    override fun getTexture(entity: MahjongRoundInfoEntity): Identifier? = null

    companion object {
        /** 文字縮放，起始估算值，預期進遊戲後用截圖比對調整。 */
        private const val TEXT_SCALE: Float = 0.025f

        /** 相鄰兩行文字之間的額外間距（像素，縮放前）。 */
        private const val LINE_SPACING: Int = 2

        /** 文字顏色，不透明白色。 */
        private const val TEXT_COLOR: Int = 0xFFFFFFFF.toInt()

        /** 文字背景色板，半透明黑底，比照 vanilla 名牌背景慣例，提升可讀性。 */
        private const val BACKGROUND_COLOR: Int = 0x60000000
    }
}
