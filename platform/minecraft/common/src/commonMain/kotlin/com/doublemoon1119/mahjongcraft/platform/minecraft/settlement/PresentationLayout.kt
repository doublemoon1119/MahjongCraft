package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 受控的宣告式布局樹；不暴露 Minecraft renderer callback。 */
sealed interface PresentationLayout {
    /**
     * 文字節點。
     *
     * @property fieldId 欄位 ID。
     * @property scale 文字縮放。
     * @property argb 文字 ARGB。
     * @property alignment 對齊方式。
     */
    data class Text(
        val fieldId: PresentationFieldId,
        val scale: Float = 1f,
        val argb: Int = 0xFFFFFFFF.toInt(),
        val alignment: PresentationAlignment = PresentationAlignment.START,
    ) : PresentationLayout {
        init {
            require(scale in 0.1f..4f)
        }
    }

    /**
     * 玩家身分節點。
     *
     * @property fieldId 欄位 ID。
     * @property showFace 是否顯示頭像。
     * @property showName 是否顯示名稱。
     * @property spacing 頭像與名稱間距。
     * @property scale 文字縮放。
     * @property argb 文字 ARGB。
     */
    data class PlayerIdentity(
        val fieldId: PresentationFieldId,
        val showFace: Boolean = true,
        val showName: Boolean = true,
        val spacing: Float = 4f,
        val scale: Float = 1f,
        val argb: Int = 0xFFFFFFFF.toInt(),
    ) : PresentationLayout {
        init {
            require(showFace || showName)
            require(spacing >= 0f && scale in 0.1f..4f)
        }
    }

    /**
     * 單張牌節點。
     *
     * @property fieldId 欄位 ID。
     * @property width 牌寬。
     * @property height 牌高。
     */
    data class Tile(
        val fieldId: PresentationFieldId,
        val width: Float = 11f,
        val height: Float = 15f,
    ) : PresentationLayout {
        init {
            require(width > 0f && height > 0f)
        }
    }

    /**
     * 多張牌列表節點。
     *
     * @property fieldId 欄位 ID。
     * @property tileWidth 單牌寬。
     * @property tileHeight 單牌高。
     * @property spacing 牌間距。
     */
    data class TileList(
        val fieldId: PresentationFieldId,
        val tileWidth: Float = 11f,
        val tileHeight: Float = 15f,
        val spacing: Float = 1.2f,
    ) : PresentationLayout {
        init {
            require(tileWidth > 0f && tileHeight > 0f && spacing >= 0f)
        }
    }

    /**
     * 多組牌列表節點。
     *
     * @property fieldId 欄位 ID。
     * @property tileWidth 單牌寬。
     * @property tileHeight 單牌高。
     * @property tileSpacing 組內牌間距。
     * @property groupSpacing 組間距。
     */
    data class TileGroups(
        val fieldId: PresentationFieldId,
        val tileWidth: Float = 11f,
        val tileHeight: Float = 15f,
        val tileSpacing: Float = 1.2f,
        val groupSpacing: Float = 5f,
    ) : PresentationLayout {
        init {
            require(tileWidth > 0f && tileHeight > 0f && tileSpacing >= 0f && groupSpacing >= 0f)
        }
    }

    /**
     * 重複條目節點。
     *
     * @property fieldId 欄位 ID。
     * @property entriesPerColumn 每欄條目數。
     * @property width 欄寬。
     * @property rowHeight 列高。
     * @property verticalAlignment 垂直對齊方式。
     */
    data class RepeatEntries(
        val fieldId: PresentationFieldId,
        val entriesPerColumn: Int = 4,
        val width: Float = 232f,
        val rowHeight: Float = 11f,
        val verticalAlignment: PresentationAlignment = PresentationAlignment.CENTER,
    ) : PresentationLayout {
        init {
            require(entriesPerColumn > 0 && width > 0f && rowHeight > 0f)
        }
    }

    /**
     * 水平排列容器。
     *
     * @property children 子節點。
     * @property spacing 子節點間距。
     * @property alignment 交叉軸對齊方式。
     * @property arrangement 主軸空間分配方式。
     * @property fillMaxWidth 是否填滿最大寬度。
     * @property style 容器外觀。
     */
    data class Row(
        val children: List<PresentationLayout>,
        val spacing: Float = 0f,
        val alignment: PresentationAlignment = PresentationAlignment.CENTER,
        val arrangement: PresentationArrangement = PresentationArrangement.START,
        val fillMaxWidth: Boolean = false,
        val style: PresentationContainerStyle = PresentationContainerStyle(),
    ) : PresentationLayout

    /**
     * 垂直排列容器。
     *
     * @property children 子節點。
     * @property spacing 子節點間距。
     * @property alignment 交叉軸對齊方式。
     * @property arrangement 主軸空間分配方式。
     * @property fillMaxHeight 是否填滿最大高度。
     * @property style 容器外觀。
     */
    data class Column(
        val children: List<PresentationLayout>,
        val spacing: Float = 0f,
        val alignment: PresentationAlignment = PresentationAlignment.CENTER,
        val arrangement: PresentationArrangement = PresentationArrangement.START,
        val fillMaxHeight: Boolean = false,
        val style: PresentationContainerStyle = PresentationContainerStyle(),
    ) : PresentationLayout

    /**
     * 在父 Row／Column 的主軸上按比例取得剩餘空間。
     *
     * @property child 被包裝的子節點。
     * @property weight 空間權重。
     * @property fill 是否填滿分配空間。
     */
    data class Weighted(
        val child: PresentationLayout,
        val weight: Float = 1f,
        val fill: Boolean = true,
    ) : PresentationLayout {
        init {
            require(weight > 0f)
        }
    }

    /**
     * 網格容器。
     *
     * @property children 子節點。
     * @property columns 欄數。
     * @property horizontalSpacing 水平間距。
     * @property verticalSpacing 垂直間距。
     * @property style 容器外觀。
     */
    data class Grid(
        val children: List<PresentationLayout>,
        val columns: Int,
        val horizontalSpacing: Float = 0f,
        val verticalSpacing: Float = 0f,
        val style: PresentationContainerStyle = PresentationContainerStyle(),
    ) : PresentationLayout {
        init {
            require(columns > 0)
        }
    }

    /**
     * 空白節點。
     *
     * @property width 寬度。
     * @property height 高度。
     */
    data class Spacer(
        val width: Float = 0f,
        val height: Float = 0f,
    ) : PresentationLayout

    /**
     * 尺寸限制節點。
     *
     * @property child 被限制的子節點。
     * @property maxWidth 最大寬度。
     * @property maxHeight 最大高度。
     */
    data class SizeConstraint(
        val child: PresentationLayout,
        val maxWidth: Float? = null,
        val maxHeight: Float? = null,
    ) : PresentationLayout

    /**
     * 可精確重建既有視覺的疊放容器；座標相對於內容區左上角。
     *
     * @property children 定位子節點。
     * @property width 容器寬度。
     * @property height 容器高度。
     * @property style 容器外觀。
     */
    data class Box(
        val children: List<Positioned>,
        val width: Float,
        val height: Float,
        val style: PresentationContainerStyle = PresentationContainerStyle(),
    ) : PresentationLayout {
        init {
            require(width > 0f && height > 0f)
        }
    }

    /**
     * [x]、[y] 指向子節點的指定錨點。
     *
     * @property child 被定位的子節點。
     * @property x X 座標。
     * @property y Y 座標。
     * @property horizontalAnchor 水平錨點。
     * @property verticalAnchor 垂直錨點。
     */
    data class Positioned(
        val child: PresentationLayout,
        val x: Float,
        val y: Float,
        val horizontalAnchor: PresentationAlignment = PresentationAlignment.START,
        val verticalAnchor: PresentationAlignment = PresentationAlignment.START,
    ) : PresentationLayout

    /**
     * 欄位有值時才建立子樹，避免可選內容留下空白 spacing。
     *
     * @property fieldId 判斷值的欄位 ID。
     * @property child 有值時建立的子樹。
     */
    data class IfPresent(
        val fieldId: PresentationFieldId,
        val child: PresentationLayout,
    ) : PresentationLayout

    /**
     * 對任意子樹套用可持久化時間線重建的受控動畫。
     *
     * @property child 被套用動畫的子樹。
     * @property timeline 動畫時間線。
     * @property effects 動畫效果。
     * @property transformOriginX 水平變形原點。
     * @property transformOriginY 垂直變形原點。
     */
    data class Animated(
        val child: PresentationLayout,
        val timeline: PresentationTimeline,
        val effects: List<PresentationAnimationEffect>,
        val transformOriginX: PresentationAlignment = PresentationAlignment.CENTER,
        val transformOriginY: PresentationAlignment = PresentationAlignment.CENTER,
    ) : PresentationLayout {
        init {
            require(effects.isNotEmpty() && effects.size <= 8)
        }
    }
}
