package doublemoon.mahjongcraft.client.gui.widget

import com.mojang.authlib.GameProfile
import doublemoon.mahjongcraft.util.RenderHelper
import io.github.cottonmc.cotton.gui.widget.WWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.block.entity.SkullBlockEntity
import net.minecraft.client.util.math.MatrixStack
import java.util.*

class WPlayerFace(
    uuid: UUID,
    name: String,
) : WWidget() {
    private var gameProfile = GameProfile(uuid, name)

    //constructor 不能含有 private set 只好這樣用
    var uuid: UUID = uuid
        private set
    var name: String = name
        private set

    init {
        loadGameProfileProperties()
    }

    fun setUuidAndName(uuid: UUID, name: String) {
        this.uuid = uuid
        this.name = name
        gameProfile = GameProfile(uuid, name)
        loadGameProfileProperties()
    }

    override fun canResize(): Boolean = true

    private fun loadGameProfileProperties() {
        CoroutineScope(Dispatchers.IO).launch {
            SkullBlockEntity.loadProperties(gameProfile)?.also { gameProfile = it }
        }
    }

    @Environment(EnvType.CLIENT)
    override fun paint(matrices: MatrixStack, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        RenderHelper.renderPlayerFace(
            matrices = matrices,
            gameProfile = gameProfile,
            x = x,
            y = y,
            width = width,
            height = height
        )
    }
}