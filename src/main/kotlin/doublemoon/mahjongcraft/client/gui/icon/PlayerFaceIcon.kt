package doublemoon.mahjongcraft.client.gui.icon

import com.mojang.authlib.GameProfile
import doublemoon.mahjongcraft.util.RenderHelper
import io.github.cottonmc.cotton.gui.widget.icon.Icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.block.entity.SkullBlockEntity
import net.minecraft.client.gui.DrawContext
import java.util.*
import kotlin.jvm.optionals.getOrNull

class PlayerFaceIcon(
    val uuid: UUID,
    val name: String,
) : Icon {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var gameProfile = GameProfile(uuid, name)

    init {
        loadGameProfileProperties()
    }

    private fun loadGameProfileProperties() {
        coroutineScope.launch(Dispatchers.IO) {
            val profile = SkullBlockEntity.fetchProfileByUuid(uuid).get().getOrNull()
                ?: SkullBlockEntity.fetchProfileByName(name).get().getOrNull()
            if (profile != null) gameProfile = profile
        }
    }

    @Environment(EnvType.CLIENT)
    override fun paint(context: DrawContext, x: Int, y: Int, size: Int) {
        RenderHelper.renderPlayerFace(
            context = context,
            gameProfile = gameProfile,
            x = x,
            y = y,
            size = size
        )
    }
}