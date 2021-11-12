package doublemoon.mahjongcraft.util

import com.mojang.authlib.GameProfile
import com.mojang.authlib.minecraft.MinecraftProfileTexture
import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawableHelper
import net.minecraft.client.gui.hud.PlayerListHud
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.item.ItemRenderer
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3f
import net.minecraft.world.LightType
import net.minecraft.world.World

@Environment(EnvType.CLIENT)
object RenderHelper {
    /**
     * 渲染物品用
     * */
    fun renderItem(
        itemRenderer: ItemRenderer,
        matrices: MatrixStack,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        stack: ItemStack,
        mode: ModelTransformation.Mode = ModelTransformation.Mode.GROUND,
        overlay: Int = OverlayTexture.DEFAULT_UV,
        light: Int,
        vertexConsumer: VertexConsumerProvider,
        seed: Int = 0
    ) {
        with(matrices) {
            push()
            translate(offsetX, offsetY, offsetZ)
            itemRenderer.renderItem(
                stack,
                mode,
                light,
                overlay,
                matrices,
                vertexConsumer,
                seed
            )
            pop()
        }
    }

    /**
     * 渲染文字用
     * */
    fun renderLabel(
        textRenderer: TextRenderer,
        matrices: MatrixStack,
        offsetX: Double,
        offsetY: Double,
        offsetZ: Double,
        text: Text,
        color: Int,
        scale: Float = 0.02f,
        backgroundColor: Int = (.4f * 255f).toInt() shl 24,
        light: Int,
        vertexConsumers: VertexConsumerProvider,
        shadow: Boolean = false,
        seeThrough: Boolean = false
    ) {
        with(matrices) {
            push()
            val client = MinecraftClient.getInstance()
            val offset = -textRenderer.getWidth(text) / 2f
            val matrix4f = matrices.peek().model
            translate(offsetX, offsetY, offsetZ)
            scale(scale, scale, scale)
            multiply(client.entityRenderDispatcher.rotation)
            multiply(Vec3f.POSITIVE_Z.getDegreesQuaternion(180f))
            textRenderer.draw(
                text,
                offset,
                0f,
                color,
                shadow,
                matrix4f,
                vertexConsumers,
                seeThrough,
                backgroundColor,
                light
            )
            pop()
        }
    }

    /**
     * 渲染玩家的臉, 參考自 [PlayerListHud]
     * @see <a href="https://forums.minecraftforge.net/topic/42871-110-get-skin-to-use-on-custom-model-from-a-given-username/">and 參考自這</a>
     * */
    fun renderPlayerFace(
        matrices: MatrixStack,
        gameProfile: GameProfile,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) {
        val client = MinecraftClient.getInstance()
        val textures = client.skinProvider.getTextures(gameProfile)
        val skinTexture = if (MinecraftProfileTexture.Type.SKIN in textures.keys) {
            val texture = textures[MinecraftProfileTexture.Type.SKIN]
            client.skinProvider.loadSkin(texture, MinecraftProfileTexture.Type.SKIN)
        } else {
            DefaultSkinHelper.getTexture(gameProfile.id)
        }
        RenderSystem.setShaderTexture(0, skinTexture)
        DrawableHelper.drawTexture(matrices, x, y, width, height, 8f, 8f, 8, 8, 64, 64)
    }

    fun getLightLevel(world: World, pos: BlockPos): Int {
        val bLight = world.lightingProvider.get(LightType.BLOCK).getLightLevel(pos)
        val sLight = world.lightingProvider.get(LightType.SKY).getLightLevel(pos)
        return LightmapTextureManager.pack(bLight, sLight)
    }
}