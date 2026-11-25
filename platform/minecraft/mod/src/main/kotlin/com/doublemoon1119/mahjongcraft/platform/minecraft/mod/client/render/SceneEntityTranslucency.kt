package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.resourceId
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * 讓原版生物繪製器以指定不透明度畫出一個不在世界中的生物，例如牌譜回放場景中擋住視線的人物。
 *
 * 只在 [render] 的工作期間生效：生物與披風改用 [renderType] 的半透明圖層，不透明度換成指定值；附加圖層只畫披風，
 * 盔甲、手持物品等其他附加圖層不畫。世界中的實體不受影響。
 *
 * 半透明圖層使用自訂著色器：原版半透明著色器會丟掉最終不透明度低於 10% 的像素，本身就半透明的部分（例如皮膚外層）
 * 在人物淡出時會整片消失；自訂著色器改以貼圖本身的不透明度判斷，淡出的人物保留不透明時看得到的每個像素。
 */
object SceneEntityTranslucency {
    /** 目前生效的不透明度；沒有生效時為 null。 */
    private var activeAlpha: Float? = null

    /** 目前載入的自訂著色器；尚未載入或載入失敗時為 null。 */
    @Volatile
    private var shader: ShaderInstance? = null

    /** 各貼圖的半透明圖層。 */
    private val renderTypes = ConcurrentHashMap<ResourceLocation, RenderType>()

    /** 自訂著色器是否已經載入。尚未載入或載入失敗時，半透明圖層改用原版的畫法。 */
    val isShaderLoaded: Boolean get() = shader != null

    /**
     * 登記半透明圖層使用的自訂著色器。
     *
     * @param events 登記用戶端事件用的事件來源。
     */
    fun registerShader(events: ClientEvents) {
        events.onRegisterCoreShader(SHADER_ID, DefaultVertexFormat.NEW_ENTITY) { shader = it }
    }

    /**
     * 以指定不透明度執行一次生物繪製。
     *
     * @param alpha 0 到 1 之間的不透明度。
     * @param draw 繪製工作。
     */
    fun render(alpha: Float, draw: () -> Unit) {
        val previous = activeAlpha
        activeAlpha = alpha.coerceIn(0f, 1f)
        try {
            draw()
        } finally {
            activeAlpha = previous
        }
    }

    /**
     * 是否正以半透明方式繪製。
     *
     * @return 生效中時為 true。
     */
    @JvmStatic
    fun isActive(): Boolean = activeAlpha != null

    /**
     * 套用到生物模型的不透明度。
     *
     * @param original 原版計算的不透明度。
     * @return 生效中時為指定值，否則為原版的值。
     */
    @JvmStatic
    fun alpha(original: Float): Float = activeAlpha ?: original

    /**
     * 指定貼圖的半透明圖層；自訂著色器尚未載入時改用原版畫看得見的隱形生物所用的圖層。
     *
     * @param texture 生物或披風的貼圖。
     * @return 半透明圖層。
     */
    @JvmStatic
    fun renderType(texture: ResourceLocation): RenderType = if (shader == null) {
        RenderType.itemEntityTranslucentCull(texture)
    } else {
        renderTypes.computeIfAbsent(texture, ::createRenderType)
    }

    /**
     * 建立單一貼圖的半透明圖層，設定與原版看得見的隱形生物相同，只換成自訂著色器並畫在主畫面上。
     *
     * @param texture 此圖層使用的貼圖。
     * @return 使用此貼圖的半透明圖層。
     */
    private fun createRenderType(texture: ResourceLocation): RenderType = RenderType.create(
        "mahjongcraft_scene_entity_translucent",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        BUFFER_SIZE,
        true,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.ShaderStateShard { shader })
            .setTextureState(RenderStateShard.TextureStateShard(texture, false, false))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(RenderStateShard.LightmapStateShard(true))
            .setOverlayState(RenderStateShard.OverlayStateShard(true))
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(true),
    )

    /** 自訂著色器的識別碼，設定檔位於 `assets/mahjongcraft/shaders/core/scene_entity_translucent.json`。 */
    private val SHADER_ID = resourceId(MinecraftModMetadata.MOD_ID, "scene_entity_translucent")

    /** 半透明圖層的頂點緩衝大小，與原版生物圖層相同。 */
    private const val BUFFER_SIZE = 256
}
