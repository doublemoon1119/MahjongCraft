package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.platform.fabric.text.toNameText
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.text.Text

/**
 * 依牌譜回覆的牌面與外觀取得素材 key；只讀基本牌面與外觀，不解碼其他特徵。
 *
 * 呼叫前須先經 [isDisplayableHistoryFace] 驗證。
 *
 * @param registry 牌面與外觀樣式的素材 registry。
 * @return 對應的素材 key。
 */
internal fun TileFaceViewDto.toHistoryAssetKey(registry: MinecraftTileAssetRegistry): String = TileFaceView(tile.toDomain(), appearanceId = appearanceId).toAssetKey(registry)

/**
 * 依牌譜回覆的牌面與外觀取得本地化牌名，用於滑鼠提示；只讀基本牌面與外觀，不解碼其他特徵。
 *
 * 呼叫前須先經 [isDisplayableHistoryFace] 驗證。
 *
 * @param displayNames 牌種與外觀樣式的名稱來源。
 * @return 本地化牌名，例如「春」或「赤5萬」。
 */
internal fun TileFaceViewDto.toHistoryNameText(displayNames: TileDisplayNameRegistry): Text = TileFaceView(tile.toDomain(), appearanceId = appearanceId).toNameText(displayNames)

/**
 * 判斷牌譜回覆的牌面能否安全繪製：基本牌面可解析，外觀 ID 格式正確。
 *
 * @return 可以繪製時為 true。
 */
internal fun TileFaceViewDto.isDisplayableHistoryFace(): Boolean = appearanceId?.let(NamespacedId::isValid) != false &&
    runCatching { tile.toDomain() }.isSuccess
