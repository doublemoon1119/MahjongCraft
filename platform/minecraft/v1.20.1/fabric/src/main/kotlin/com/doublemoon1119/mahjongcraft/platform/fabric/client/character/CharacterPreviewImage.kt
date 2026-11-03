package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSource

/**
 * 已截取的公開模型圖片，關閉時只釋放圖片資源。
 *
 * @property full 完整模型圖片。
 * @property portrait 小頭像取圖區域。
 * @property dispose 圖片擁有者提供的釋放操作。
 */
internal class CharacterPreviewImage(
    val full: PlayerPortraitSource.TextureRegion,
    val portrait: PlayerPortraitSource.TextureRegion,
    private val dispose: () -> Unit,
) : AutoCloseable {
    /** 防止多個清理入口重複釋放貼圖。 */
    private var closed = false

    override fun close() {
        if (!closed) {
            closed = true
            dispose()
        }
    }
}
