package kim.bifrost.rain.plugman

import kim.bifrost.rain.plugman.command.CommandHandler
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.utils.info

object PlugMan : KotlinPlugin(
    JvmPluginDescription(
        id = "kim.bifrost.rain.plugman",
        name = "PluginManager",
        version = "1.0-SNAPSHOT",
    ) {
        author("Rain")
        info("""Hot fix solution for mirai console""")
    }
) {
    override fun onEnable() {
        CommandHandler.init()
    }
}