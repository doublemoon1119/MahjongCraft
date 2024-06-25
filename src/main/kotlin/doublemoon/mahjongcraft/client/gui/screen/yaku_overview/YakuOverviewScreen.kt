package doublemoon.mahjongcraft.client.gui.screen.yaku_overview

import doublemoon.mahjongcraft.client.gui.widget.*
import doublemoon.mahjongcraft.game.mahjong.riichi.model.MahjongTile
import io.github.cottonmc.cotton.gui.client.CottonClientScreen
import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription
import io.github.cottonmc.cotton.gui.widget.WButton
import io.github.cottonmc.cotton.gui.widget.WPlainPanel
import io.github.cottonmc.cotton.gui.widget.WScrollPanel
import io.github.cottonmc.cotton.gui.widget.data.Color
import io.github.cottonmc.cotton.gui.widget.data.HorizontalAlignment
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Formatting
import kotlin.math.floor

@Environment(EnvType.CLIENT)
class YakuOverviewScreen : CottonClientScreen(YakuOverviewGui()) {
    override fun shouldPause(): Boolean = false
}

@Environment(EnvType.CLIENT)
class YakuOverviewGui : LightweightGuiDescription() {
    private val client = MinecraftClient.getInstance()
    private val textRenderer = client.textRenderer
    private val fontHeight = textRenderer.fontHeight

    private val tabButtons = mutableMapOf<OverviewTab, WButton>()
    private var currentTab = OverviewTab.Han1

    private lateinit var contentPanel: WPlainPanel
    private var itemsPanel: WScrollPanel? = null

    init {
        rootPlainPanel(width = ROOT_WIDTH, height = ROOT_HEIGHT) {
            // Tabs
            val tabs = OverviewTab.entries.toTypedArray()
            val tabsPanel = scrollPanel(
                x = BORDER_MARGIN,
                y = BORDER_MARGIN,
                width = AVAILABLE_WIDTH,
                height = BUTTON_HEIGHT + SCROLL_BAR_SIZE
            ) {
                var x = 0
                for (tab in tabs) {
                    val tabWidth = textRenderer.getWidth(tab.title) + BUTTON_TEXT_PADDING * 2
                    val tabHeight = BUTTON_HEIGHT
                    val buttonWidget = button(
                        x = 0,
                        y = 0,
                        width = tabWidth,
                        height = tabHeight,
                        label = tab.title,
                        enabled = tab != currentTab,
                        onClick = { switchTab(tab) },
                    )
                    tabButtons[tab] = buttonWidget
                    this.add(buttonWidget, x, 0, tabWidth, tabHeight)
                    x += tabWidth
                }
            }

            // Content
            contentPanel = plainPanel(
                x = tabsPanel.x,
                y = tabsPanel.y + tabsPanel.height + CONTENT_PANEL_PADDING,
                width = AVAILABLE_WIDTH,
                height = AVAILABLE_HEIGHT - tabsPanel.height - CONTENT_PANEL_PADDING
            )
            switchTab(currentTab)
        }
    }

    private fun switchTab(tab: OverviewTab) {
        currentTab = tab
        for ((_tab, button) in tabButtons) {
            button.isEnabled = _tab != currentTab
        }

        // 切換 Tab 對應的內容
        itemsPanel?.let { contentPanel.remove(it) }
        itemsPanel = contentPanel.scrollPanel(
            x = 0,
            y = 0,
            width = contentPanel.width,
            height = contentPanel.height
        ) {
            applyContent(currentTab)
        }
        itemsPanel?.host = this
    }

    private fun WPlainPanel.applyContent(tab: OverviewTab) {
        var y = 0
        for (item in tab.items) {
            // Title & Subtitle
            val titleHorizontalPadding = 8
            val titleVerticalPadding = 4

            // Title & subtitle 的背景顏色
            colorBlock(
                x = 0,
                y = y,
                width = AVAILABLE_WIDTH - SCROLL_BAR_SIZE,
                height = fontHeight + 2 * titleVerticalPadding,
                color = 0xFF_939393.toInt(),
            )

            val title = text(
                x = titleHorizontalPadding,
                y = y + titleVerticalPadding,
                height = fontHeight,
                text = item.title.copy().formatted(Formatting.BOLD),
                color = 0xbc3033
            )

            val subtitleX = title.x + title.width
            text(
                x = subtitleX,
                y = title.y,
                width = AVAILABLE_WIDTH - subtitleX - SCROLL_BAR_SIZE - titleHorizontalPadding,
                height = fontHeight,
                text = item.subtitle.text.copy().formatted(Formatting.BOLD),
                color = 0x4ae168,
                horizontalAlignment = HorizontalAlignment.RIGHT
            )

            // Description
            val descriptionHorizontalPadding = 12
            val descriptionVerticalPadding = 6
            val description = text(
                x = descriptionHorizontalPadding,
                y = title.y + title.height + titleVerticalPadding + descriptionVerticalPadding,
                width = AVAILABLE_WIDTH - SCROLL_BAR_SIZE - descriptionHorizontalPadding * 2,
                text = item.description
            )

            // Tiles
            val tilesHorizontalPadding = 12
            val tilesVerticalPadding = 6
            val tilesPanelWidth = AVAILABLE_WIDTH - tilesHorizontalPadding * 2

            // 麻將牌可能會超過最大寬度 tilesPanelWidth
            val tilesWidth = item.tiles.sumOf { tileSet ->
                tileSet.size * (TILE_WIDTH + TILE_GAP) + TILE_LIST_INTERVAL
            }

            val (tileWidth, tileHeight) = if (tilesWidth <= tilesPanelWidth) {
                TILE_WIDTH to TILE_HEIGHT
            } else {
                // 超出最大寬度的話，等比例縮小至符合最大寬度
                val scale = tilesPanelWidth.toDouble() / tilesWidth.toDouble()
                floor(TILE_WIDTH * scale).toInt() to floor(TILE_HEIGHT * scale).toInt()
            }

            val tilesPanel = plainPanel(
                x = tilesHorizontalPadding,
                y = description.y + description.height + descriptionVerticalPadding + tilesVerticalPadding,
                width = tilesPanelWidth,
                height = tileHeight + SCROLL_BAR_SIZE
            ) {
                var offsetX = 0
                for (tileSet in item.tiles) {
                    for (tile in tileSet) {
                        val tileWidget = if (tile != MahjongTile.UNKNOWN) {
                            mahjongTile(
                                x = offsetX,
                                y = 0,
                                mahjongTile = tile,
                                width = tileWidth,
                                height = tileHeight,
                            )
                        } else {  // 如果是 MahjongTile.UNKNOWN 僅顯示一個色塊，目前只有`寶牌`含有這東西
                            colorBlock(
                                x = offsetX,
                                y = 0,
                                color = Color.GREEN,
                                width = tileWidth,
                                height = tileHeight
                            )
                        }
                        offsetX += tileWidget.width + TILE_GAP
                    }
                    offsetX += TILE_LIST_INTERVAL
                }
            }

            // 最後記得把高度加到 y
            y = tilesPanel.y + tilesPanel.height
        }
    }

    companion object {
        private const val ROOT_WIDTH = 400
        private const val ROOT_HEIGHT = 200
        private const val BORDER_MARGIN = 8
        private const val AVAILABLE_WIDTH = ROOT_WIDTH - 2 * BORDER_MARGIN
        private const val AVAILABLE_HEIGHT = ROOT_HEIGHT - 2 * BORDER_MARGIN

        private const val CONTENT_PANEL_PADDING = 8

        //        private const val BUTTON_WIDTH = 80
        private const val BUTTON_HEIGHT = 20

        private const val BUTTON_TEXT_PADDING = 16
        private const val TILE_SCALE = 0.42f //這個值要觀察一下
        private const val TILE_WIDTH = (48 * TILE_SCALE).toInt()
        private const val TILE_HEIGHT = (64 * TILE_SCALE).toInt()
        private const val TILE_GAP = TILE_WIDTH / 12
        private const val TILE_LIST_INTERVAL = TILE_WIDTH / 12 * 4

        private const val SCROLL_BAR_SIZE = 8
    }
}
