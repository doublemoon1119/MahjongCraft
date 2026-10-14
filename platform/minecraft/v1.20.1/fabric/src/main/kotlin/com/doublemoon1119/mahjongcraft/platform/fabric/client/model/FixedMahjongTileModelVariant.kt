package com.doublemoon1119.mahjongcraft.platform.fabric.client.model

import net.minecraft.client.render.model.json.JsonUnbakedModel
import net.minecraft.util.Identifier

/**
 * 固定牌模型的材質變體描述。
 *
 * 變體只覆寫固定模板的正面、牌體圖集與粒子材質；模型的 parent、幾何與 display
 * 轉換仍由模板提供，因此不開放改變牌的尺寸或放置契約。
 *
 * @property modelId 資源重載期間用來識別此合成模型的 synthetic model ID。
 * @property templateModelId 維持固定幾何與 display 的模型模板 ID。
 * @property frontTextureId 可見牌面材質的完整資源 ID。
 * @property coverTextureId 牌組共用牌體圖集的完整資源 ID。
 */
class FixedMahjongTileModelVariant(
    val modelId: Identifier,
    val templateModelId: Identifier,
    val frontTextureId: Identifier,
    val coverTextureId: Identifier,
) {
    /**
     * 建立供 Fabric model resolver 解析的 JSON 未烘焙模型。
     *
     * @return 只覆寫材質槽、並保留模板 parent 的未烘焙模型。
     */
    fun toUnbakedModel(): JsonUnbakedModel = JsonUnbakedModel.deserialize(
        """
        {
          "parent": "$templateModelId",
          "textures": {
            "0": "${frontTextureId.toModelTextureReference()}",
            "1": "${coverTextureId.toModelTextureReference()}",
            "particle": "${coverTextureId.toModelTextureReference()}"
          }
        }
        """.trimIndent(),
    ).also { it.id = modelId.toString() }

    /**
     * 將完整貼圖資源 ID 轉換為 Minecraft model JSON 的材質參照格式。
     *
     * @return 不含 `textures/` 前綴與 `.png` 後綴的材質參照。
     */
    private fun Identifier.toModelTextureReference(): String {
        val originalPath = getPath()
        val path = originalPath.removePrefix("textures/").removeSuffix(".png")
        return "${getNamespace()}:$path"
    }
}
