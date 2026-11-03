package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.FabricAiCharacterPreviewHandle
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSource
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.systems.VertexSorter
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.SimpleFramebuffer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.LivingEntity
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
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
    fun create(world: ClientWorld, character: AiCharacterDefinition): FabricAiCharacterPreviewHandle? {
        val capability = character.preview?.capabilityId ?: return null
        val provider = registry.find(capability)?.provider ?: return null
        val handle = provider.create(world, character) ?: return null
        val entity = handle.entity
        check(entity.world === world && world.getEntityById(entity.id) !== entity) { "Preview factory returned a live world entity" }
        try {
            check(!entity.hasVehicle() && !entity.hasPassengers()) { "Preview entity must be independent" }
            check(entity.height.isFinite() && entity.width.isFinite() && entity.height > 0f && entity.width > 0f && entity.height <= 128f && entity.width <= 128f) { "Invalid preview dimensions" }
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
    fun capture(world: ClientWorld, character: AiCharacterDefinition): CharacterPreviewImage? {
        val client = MinecraftClient.getInstance()
        check(client.isOnThread) { "Character preview requires the client thread" }
        var completed: CharacterPreviewImage? = null
        return try {
            CharacterPreviewRenderState().use {
                val handle = create(world, character) ?: return@use null
                val entity = handle.entity
                // 現場實體的違約 handle 不執行可能刪除該實體的 close。
                check(entity.world === world && world.getEntityById(entity.id) !== entity) { "Preview factory returned a live world entity" }
                AutoCloseable { handle.close() }.use {
                    check(!entity.hasVehicle() && !entity.hasPassengers()) { "Preview entity must be independent" }
                    check(entity.height.isFinite() && entity.width.isFinite() && entity.height > 0f && entity.width > 0f && entity.height <= 128f && entity.width <= 128f) { "Invalid preview dimensions" }
                    entity.yaw = 180f
                    entity.prevYaw = 180f
                    if (entity is LivingEntity) {
                        entity.bodyYaw = 180f
                        entity.prevBodyYaw = 180f
                        entity.headYaw = 180f
                        entity.prevHeadYaw = 180f
                    }
                    val region = handle.portraitRegion
                    var framebuffer: SimpleFramebuffer? = null
                    AutoCloseable { framebuffer?.delete() }.use {
                        val target = SimpleFramebuffer(SIZE, SIZE, true, MinecraftClient.IS_SYSTEM_MAC).also { framebuffer = it }
                        target.setClearColor(0f, 0f, 0f, 0f)
                        target.clear(MinecraftClient.IS_SYSTEM_MAC)
                        target.beginWrite(true)
                        // 離屏圖片使用自己的投影與座標原點，不沿用 GUI 深度位移。
                        RenderSystem.getModelViewStack().loadIdentity()
                        RenderSystem.applyModelViewMatrix()
                        RenderSystem.setProjectionMatrix(Matrix4f().setOrtho(0f, SIZE.toFloat(), SIZE.toFloat(), 0f, -1000f, 1000f), VertexSorter.BY_Z)
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
                        RenderSystem.disableScissor()
                        RenderSystem.enableDepthTest()
                        RenderSystem.depthMask(true)
                        RenderSystem.enableBlend()
                        RenderSystem.defaultBlendFunc()
                        val matrices = MatrixStack()
                        val scale = (SIZE * 0.8f) / maxOf(entity.height, entity.width)
                        matrices.translate(SIZE / 2.0, SIZE * 0.9, 0.0)
                        matrices.scale(scale, -scale, scale)
                        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f))
                        val dispatcher = client.entityRenderDispatcher
                        dispatcher.setRotation(Quaternionf().rotationY(Math.PI.toFloat()))
                        dispatcher.setRenderShadows(false)
                        dispatcher.setRenderHitboxes(false)
                        val consumers = client.bufferBuilders.entityVertexConsumers
                        try {
                            dispatcher.render(entity, 0.0, 0.0, 0.0, 180f, 0f, matrices, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE)
                        } finally {
                            consumers.draw()
                        }
                        RenderSystem.bindTexture(target.colorAttachment)
                        val image = NativeImage(SIZE, SIZE, false)
                        var texture: NativeImageBackedTexture? = null
                        var registered: Identifier? = null
                        AutoCloseable {
                            try {
                                texture?.close()
                            } finally {
                                registered?.let(client.textureManager::destroyTexture)
                            }
                        }.use {
                            try {
                                image.loadFromTextureImage(0, false)
                                image.mirrorVertically()
                                texture = NativeImageBackedTexture(image)
                            } catch (cause: Throwable) {
                                image.close()
                                throw cause
                            }
                            val id = client.textureManager.registerDynamicTexture("mahjongcraft_character_preview", texture)
                            registered = id
                            texture = null
                            val captured = CharacterPreviewImage(
                                full = PlayerPortraitSource.TextureRegion(id.toString(), 0f, 0f, 1f, 1f),
                                portrait = PlayerPortraitSource.TextureRegion(id.toString(), region?.left ?: 0f, region?.top ?: 0f, region?.right ?: 1f, region?.bottom ?: 1f),
                                dispose = { client.textureManager.destroyTexture(id) },
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
