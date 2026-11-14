package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationTile

/**
 * 把和牌的手牌與副露排成模板使用的牌組；蓋著的牌換成牌背，不把牌面交給模板。
 *
 * @param handAssetKeys 手牌牌面素材，依結算順序排列。
 * @param melds 各組副露。
 * @param backAppearanceOf 依牌面素材取得蓋著時要畫的牌背外觀 ID。
 * @return 手牌在前、之後依序為各組副露的牌組；空的組不列入。
 */
internal fun winSettlementTileGroups(
    handAssetKeys: List<String>,
    melds: List<WinSettlementMeldSnapshot>,
    backAppearanceOf: (String) -> String,
): List<List<PresentationTile>> = buildList {
    add(handAssetKeys.map(PresentationTile::Face))
    melds.forEach { meld ->
        add(
            element = meld.assetKeys.mapIndexed { index, asset ->
                if (index in meld.faceDownIndices) PresentationTile.Back(backAppearanceOf(asset)) else PresentationTile.Face(asset)
            },
        )
    }
}.filter(List<PresentationTile>::isNotEmpty)
