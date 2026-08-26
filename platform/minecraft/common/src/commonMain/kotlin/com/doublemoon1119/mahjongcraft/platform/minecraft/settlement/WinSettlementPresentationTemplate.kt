package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** 胡牌結算模板可讀取的完整 namespaced 欄位識別碼。 */
@JvmInline
value class PresentationFieldId(val value: String) {
    init {
        require(value.substringBefore(':', "").isNotBlank() && value.substringAfter(':', "").isNotBlank()) {
            "Presentation field ID must be namespaced: $value"
        }
    }

    override fun toString(): String = value
}

/** 宣告式模板唯一允許使用的受控顯示值。 */
sealed interface PresentationValue {
    /** 本地化文字；[arguments] 只包含已序列化的顯示參數。 */
    data class TextValue(val translationKey: String, val arguments: List<String> = emptyList()) : PresentationValue

    /** 單張牌面 asset。 */
    data class TileValue(val assetKey: String) : PresentationValue

    /** 保留順序的多張牌面 asset。 */
    data class TileListValue(val assetKeys: List<String>) : PresentationValue

    /** 保留手牌、副露及牌背語意的多組牌面。 */
    data class TileGroupsValue(val groups: List<List<String>>) : PresentationValue

    /** 玩家識別快照；renderer 可安全顯示 FACE 與名稱，不需要接觸 PlayerEntity。 */
    data class PlayerIdentityValue(
        val playerId: String,
        val displayName: String,
        val isAi: Boolean,
    ) : PresentationValue

    /** 可逐項揭曉的文字條目。 */
    data class EntryListValue(val entries: List<Entry>) : PresentationValue {
        /** 單一條目的本地化名稱與右側值。 */
        data class Entry(
            val translationKey: String,
            val trailingText: String = "",
            val trailingTranslationKey: String? = null,
            val trailingTranslationArgument: String? = null,
        )
    }
}

/** 模板布局的對齊方式。 */
enum class PresentationAlignment { START, CENTER, END }

/** 主軸剩餘空間的分配方式，語意比照 Compose 的 Arrangement。 */
enum class PresentationArrangement { START, CENTER, END, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY }

/** 動畫起點所依附的穩定時間錨點。 */
enum class PresentationTimelineAnchor { PANEL_START, ENTRIES_START, AFTER_ENTRIES, SCORE_REVEAL }

/** 以相對錨點描述的節點時間範圍。 */
data class PresentationTimeline(
    val anchor: PresentationTimelineAnchor = PresentationTimelineAnchor.PANEL_START,
    val offsetTicks: Int = 0,
    val durationTicks: Int = 8,
) {
    init {
        require(offsetTicks in -1200..1200)
        require(durationTicks in 1..400)
    }
}

/** renderer 支援的受控動畫效果；第三方不需要也不能提供任意 callback。 */
sealed interface PresentationAnimationEffect {
    data class Fade(val fromAlpha: Float = 0f, val toAlpha: Float = 1f) : PresentationAnimationEffect {
        init {
            require(fromAlpha in 0f..1f && toAlpha in 0f..1f)
        }
    }

    data class Slide(val fromX: Float = 0f, val fromY: Float = 0f) : PresentationAnimationEffect {
        init {
            require(fromX in -320f..320f && fromY in -240f..240f)
        }
    }

    /** 依進度線性插值的縮放關鍵影格。 */
    data class ScaleKeyframes(val keyframes: List<ScaleKeyframe>) : PresentationAnimationEffect {
        init {
            require(keyframes.size in 2..8)
            require(keyframes.first().progress == 0f && keyframes.last().progress == 1f)
            require(keyframes.zipWithNext().all { (left, right) -> left.progress < right.progress })
        }
    }

    /** 在節點背景上疊加一次透明度脈衝。 */
    data class BackgroundPulse(val argb: Int) : PresentationAnimationEffect

    /** 由左至右掃過節點的高光。 */
    data class HighlightSweep(val argb: Int = 0x66FFFFFF, val width: Float = 18f) : PresentationAnimationEffect {
        init {
            require(width in 1f..160f)
        }
    }

    /** 以水平縮放模擬由中心揭曉，避免暴露底層裁切 API。 */
    data class HorizontalReveal(val fromScale: Float = 0f) : PresentationAnimationEffect {
        init {
            require(fromScale in 0f..1f)
        }
    }
}

/** 單一縮放關鍵影格。 */
data class ScaleKeyframe(val progress: Float, val scale: Float) {
    init {
        require(progress in 0f..1f && scale in 0f..4f)
    }
}

/** 可選容器外觀；預設完全透明且沒有框線或 padding。 */
data class PresentationContainerStyle(
    val backgroundArgb: Int = 0x00000000,
    val borderArgb: Int = 0x00000000,
    val borderWidth: Float = 0f,
    val padding: Float = 0f,
    val dividerArgb: Int = 0x00000000,
    val dividerWidth: Float = 0f,
)

/** 受控的宣告式布局樹；不暴露 Minecraft renderer callback。 */
sealed interface PresentationLayout {
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
    data class Tile(
        val fieldId: PresentationFieldId,
        val width: Float = 11f,
        val height: Float = 15f,
    ) : PresentationLayout {
        init {
            require(width > 0f && height > 0f)
        }
    }
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

    data class Row(
        val children: List<PresentationLayout>,
        val spacing: Float = 0f,
        val alignment: PresentationAlignment = PresentationAlignment.CENTER,
        val arrangement: PresentationArrangement = PresentationArrangement.START,
        val fillMaxWidth: Boolean = false,
        val style: PresentationContainerStyle = PresentationContainerStyle(),
    ) : PresentationLayout

    data class Column(
        val children: List<PresentationLayout>,
        val spacing: Float = 0f,
        val alignment: PresentationAlignment = PresentationAlignment.CENTER,
        val arrangement: PresentationArrangement = PresentationArrangement.START,
        val fillMaxHeight: Boolean = false,
        val style: PresentationContainerStyle = PresentationContainerStyle(),
    ) : PresentationLayout

    /** 在父 Row／Column 的主軸上按比例取得剩餘空間。 */
    data class Weighted(val child: PresentationLayout, val weight: Float = 1f, val fill: Boolean = true) : PresentationLayout {
        init {
            require(weight > 0f)
        }
    }

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

    data class Spacer(val width: Float = 0f, val height: Float = 0f) : PresentationLayout
    data class SizeConstraint(val child: PresentationLayout, val maxWidth: Float? = null, val maxHeight: Float? = null) : PresentationLayout

    /** 可精確重建既有視覺的疊放容器；座標相對於內容區左上角。 */
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

    /** [x]、[y] 指向子節點的指定錨點。 */
    data class Positioned(
        val child: PresentationLayout,
        val x: Float,
        val y: Float,
        val horizontalAnchor: PresentationAlignment = PresentationAlignment.START,
        val verticalAnchor: PresentationAlignment = PresentationAlignment.START,
    ) : PresentationLayout

    /** 欄位有值時才建立子樹，避免可選內容留下空白 spacing。 */
    data class IfPresent(val fieldId: PresentationFieldId, val child: PresentationLayout) : PresentationLayout

    /** 對任意子樹套用可持久化時間線重建的受控動畫。 */
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

/** 在指定錨點播放的宣告式聲音。 */
data class PresentationSoundCue(
    val soundId: String,
    val anchor: PresentationTimelineAnchor,
    val offsetTicks: Int = 0,
    val volume: Float = 0.35f,
    val pitch: Float = 1f,
) {
    init {
        require(soundId.substringBefore(':', "").isNotBlank() && soundId.substringAfter(':', "").isNotBlank())
        require(offsetTicks in -1200..1200 && volume in 0f..4f && pitch in 0.1f..4f)
    }
}

/** 模板的時間與音效設定。 */
data class WinSettlementRevealSequence(
    val initialFadeTicks: Int = 16,
    val entryStaggerTicks: Int = 8,
    val scoreRevealTicks: Int = 18,
    val readingTicks: Int = 60,
    val entrySoundId: String? = null,
    val scoreSoundId: String? = null,
    val soundVolume: Float = 0.35f,
    val soundPitch: Float = 1f,
    val sounds: List<PresentationSoundCue> = emptyList(),
) {
    init {
        require(initialFadeTicks >= 0 && entryStaggerTicks >= 0 && scoreRevealTicks >= 0 && readingTicks >= 0)
        require(soundVolume >= 0f && soundPitch > 0f)
        require(sounds.size <= 32)
    }
}

/** 第三方可完整替換的胡牌結算面板模板。 */
data class WinSettlementPresentationTemplate(
    val key: String,
    val root: PresentationLayout,
    val reveal: WinSettlementRevealSequence = WinSettlementRevealSequence(),
) {
    init {
        require(key.substringBefore(':', "").isNotBlank() && key.substringAfter(':', "").isNotBlank()) {
            "Win settlement template key must be namespaced: $key"
        }
    }
}
