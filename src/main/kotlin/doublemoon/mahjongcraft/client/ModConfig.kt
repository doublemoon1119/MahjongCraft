package doublemoon.mahjongcraft.client

import doublemoon.mahjongcraft.MOD_ID
import me.shedaniel.autoconfig.ConfigData
import me.shedaniel.autoconfig.annotation.Config

@Config(name = MOD_ID)
data class ModConfig(
    val displayTableLabels: Boolean = true,
) : ConfigData