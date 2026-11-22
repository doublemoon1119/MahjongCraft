package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.AiCharacterPreviewHandle
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character.CharacterPreviewImage
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSource
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexSorting
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import org.joml.Matrix4f
import org.joml.Quaternionf

/**
 * 將獨立模型截成有透明背景的圖片，不將預覽實體加入世界，也不執行 tick。
 *
 * @property registry 只在客戶端初始化的工廠。
 */
internal class NativeCharacterPreviewRenderer(private val registry: ClientAiCharacterPreviewRegistry) {
    /**
     * 由公開參數建立獨立備援；違約返回本人時不呼叫可能影響本人的 close。
     *
     * @param world 目前客戶端世界。
     * @param character 提供者自行解讀的公開外觀描述。
     * @return 經檢查的預覽 handle，能力不可用時為 null。
     */
    fun create(world: ClientLevel, character: AiCharacterDefinition): AiCharacterPreviewHandle? {
        val capability = character.preview?.capabilityId ?: return null
        val provider = registry.find(capability)?.provider ?: return null
        val handle = provider.create(world, character) ?: return null
        val entity = handle.entity
        check(entity.level() === world && world.getEntity(entity.id) !== entity) { "Preview factory returned a live world entity" }
        try {
            check(!entity.isPassenger && !entity.isVehicle) { "Preview entity must be independent" }
            check(entity.bbHeight.isFinite() && entity.bbWidth.isFinite() && entity.bbHeight > 0f && entity.bbWidth > 0f && entity.bbHeight <= 128f && entity.bbWidth <= 128f) { "Invalid preview dimensions" }
        } catch (cause: Throwable) {
            try {
                handle.close()
            } catch (cleanup: Throwable) {
                if (cleanup !== cause) cause.addSuppressed(cleanup)
            }
            throw cause
        }
        return handle
    }

    /**
     * 擷取完整模型與可選頭像區域，所有暫態模型與繪製狀態在返回前釋放。
     *
     * @param world 目前連線的客戶端世界。
     * @param character 當場已保存的公開描述。
     * @return 共用一張圖片的完整與頭像來源；能力不可用時為 null。
     */
    fun capture(world: ClientLevel, character: AiCharacterDefinition): CharacterPreviewImage? {
        val client = Minecraft.getInstance()
        check(client.isSameThread) { "Character preview requires the client thread" }
        var completed: CharacterPreviewImage? = null
        return try {
            CharacterPreviewRenderState().use {
                val handle = create(world, character) ?: return@use null
                val entity = handle.entity
                // 現場實體的違約 handle 不執行可能刪除該實體的 close。
                check(entity.level() === world && world.getEntity(entity.id) !== entity) { "Preview factory returned a live world entity" }
                AutoCloseable { handle.close() }.use {
                    check(!entity.isPassenger && !entity.isVehicle) { "Preview entity must be independent" }
                    check(entity.bbHeight.isFinite() && entity.bbWidth.isFinite() && entity.bbHeight > 0f && entity.bbWidth > 0f && entity.bbHeight <= 128f && entity.bbWidth <= 128f) { "Invalid preview dimensions" }
                    entity.setYRot(180f)
                    entity.yRotO = 180f
                    if (entity is LivingEntity) {
                        entity.yBodyRot = 180f
                        entity.yBodyRotO = 180f
                        entity.yHeadRot = 180f
                        entity.yHeadRotO = 180f
                    }
                    val region = handle.portraitRegion
                    var framebuffer: TextureTarget? = null
                    AutoCloseable { framebuffer?.destroyBuffers() }.use {
                        val target = TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX).also { framebuffer = it }
                        target.setClearColor(0f, 0f, 0f, 0f)
                        target.clear(Minecraft.ON_OSX)
                        target.bindWrite(true)
                        // 離屏圖片使用自己的投影與座標原點，不沿用 GUI 深度位移。
                        RenderSystem.getModelViewStack().setIdentity()
                        RenderSystem.applyModelViewMatrix()
                        RenderSystem.setProjectionMatrix(Matrix4f().setOrtho(0f, SIZE.toFloat(), SIZE.toFloat(), 0f, -1000f, 1000f), VertexSorting.ORTHOGRAPHIC_Z)
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
                        RenderSystem.disableScissor()
                        RenderSystem.enableDepthTest()
                        RenderSystem.depthMask(true)
                        RenderSystem.enableBlend()
                        RenderSystem.defaultBlendFunc()
                        val matrices = PoseStack()
                        val scale = (SIZE * 0.8f) / maxOf(entity.bbHeight, entity.bbWidth)
                        matrices.translate(SIZE / 2.0, SIZE * 0.9, 0.0)
                        matrices.scale(scale, -scale, scale)
                        matrices.mulPose(Axis.YP.rotationDegrees(180f))
                        val dispatcher = client.entityRenderDispatcher
                        dispatcher.overrideCameraOrientation(Quaternionf().rotationY(Math.PI.toFloat()))
                        dispatcher.setRenderShadow(false)
                        dispatcher.setRenderHitBoxes(false)
                        val consumers = client.renderBuffers().bufferSource()
                        try {
                            dispatcher.render(entity, 0.0, 0.0, 0.0, 180f, 0f, matrices, consumers, LightTexture.FULL_BRIGHT)
                        } finally {
                            consumers.endBatch()
                        }
                        RenderSystem.bindTexture(target.colorTextureId)
                        val image = NativeImage(SIZE, SIZE, false)
                        var texture: DynamicTexture? = null
                        var registered: ResourceLocation? = null
                        AutoCloseable {
                            try {
                                texture?.close()
                            } finally {
                                registered?.let(client.textureManager::release)
                            }
                        }.use {
                            try {
                                image.downloadTexture(0, false)
                                image.flipY()
                                texture = DynamicTexture(image)
                            } catch (cause: Throwable) {
                                image.close()
                                throw cause
                            }
                            val id = client.textureManager.register("mahjongcraft_character_preview", texture)
                            registered = id
                            texture = null
                            val captured = CharacterPreviewImage(
                                full = PlayerPortraitSource.TextureRegion(id.toString(), 0f, 0f, 1f, 1f),
                                portrait = PlayerPortraitSource.TextureRegion(id.toString(), region?.left ?: 0f, region?.top ?: 0f, region?.right ?: 1f, region?.bottom ?: 1f),
                                dispose = { client.textureManager.release(id) },
                            )
                            completed = captured
                            registered = null
                            captured
                        }
                    }
                }
            }
        } catch (cause: Throwable) {
            try {
                completed?.close()
            } catch (cleanup: Throwable) {
                if (cleanup !== cause) cause.addSuppressed(cleanup)
            }
            throw cause
        }
    }

    /** 每份圖片的固定像素大小。 */
    private companion object {
        /** 完整模型與頭像共用一張圖片。 */
        const val SIZE: Int = 128
    }
}
