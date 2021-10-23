package doublemoon.mahjongcraft.registry

import doublemoon.mahjongcraft.id
import net.minecraft.sound.SoundEvent
import net.minecraft.util.registry.Registry

object SoundRegistry {

    //TODO 缺少了一些音效，待日後補上
    val chii = SoundEvent(id("chii"))
    val pon = SoundEvent(id("pon"))
    val kan = SoundEvent(id("kan"))
    val riichi = SoundEvent(id("riichi"))
    val ron = SoundEvent(id("ron"))
    val tsumo = SoundEvent(id("tsumo"))


    fun register() {
        Registry.register(Registry.SOUND_EVENT, id("chii"), chii)
        Registry.register(Registry.SOUND_EVENT, id("pon"), pon)
        Registry.register(Registry.SOUND_EVENT, id("kan"), kan)
        Registry.register(Registry.SOUND_EVENT, id("riichi"), riichi)
        Registry.register(Registry.SOUND_EVENT, id("ron"), ron)
        Registry.register(Registry.SOUND_EVENT, id("tsumo"), tsumo)
    }

}