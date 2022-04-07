package kim.bifrost.rain.plugman.command

import kim.bifrost.rain.plugman.HotFixHandler
import kim.bifrost.rain.plugman.PlugMan
import kotlinx.coroutines.CoroutineScope
import net.mamoe.mirai.console.plugin.author
import net.mamoe.mirai.console.plugin.name
import net.mamoe.mirai.console.plugin.version
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.MessageSource.Key.quote

/**
 * kim.bifrost.rain.plugman.command.CommandHandler
 * plugman
 *
 * @author 寒雨
 * @since 2022/4/6 19:53
 **/
object CommandHandler : CoroutineScope by PlugMan {

    private val commandMap = mutableMapOf<String, suspend GroupMessageEvent.(args: List<String>) -> Unit>()

    private fun command(command: String, executor: suspend GroupMessageEvent.(args: List<String>) -> Unit) {
        commandMap[command] = executor
    }

    fun init() {
        // 订阅
        GlobalEventChannel
            .parentScope(this)
            .subscribeAlways<GroupMessageEvent> {
                val msg = message.contentToString()
                val cmd = commandMap.keys.find { msg.startsWith("/$it ") }
                if (cmd != null) {
                    commandMap[cmd]!!(msg.removePrefix("/$cmd ").split(" "))
                }
            }
        // 注册命令
        command("plugman") { args ->
            if (args.isNotEmpty()) {
                when(args[0]) {
                    "load" -> {
                        kotlin.runCatching {
                            val name = (args.getOrNull(1) ?: return@command) + ".jar"
                            val pluginFile = HotFixHandler.pluginFolder.listFiles()?.find { it.name == name }
                            if (pluginFile == null) {
                                group.sendMessage("没有找到插件: $name")
                                return@command
                            }
                            HotFixHandler.loadPlugin(pluginFile)
                            name
                        }.onFailure {
                            group.sendMessage("加载插件时发生错误: ${it.message}")
                        }.onSuccess {
                            group.sendMessage("成功加载插件: $it")
                        }
                    }
                    "unload" -> {
                        kotlin.runCatching {
                            val name = args.getOrNull(1) ?: return@command
                            HotFixHandler.unloadPlugin(name)
                            name
                        }.onFailure {
                            group.sendMessage("卸载插件时发生错误: ${it.message}")
                            it.printStackTrace()
                        }.onSuccess {
                            group.sendMessage("成功卸载插件: $it")
                        }
                    }
                    "enable" -> {
                        kotlin.runCatching {
                            val plugin = HotFixHandler.loadedPlugins.find { it.name == args.getOrNull(1) } ?: return@command let {
                                group.sendMessage(message.quote() + "插件不存在")
                            }
                            HotFixHandler.enable(plugin)
                            plugin
                        }.onFailure {
                            group.sendMessage("启用插件时发生错误: ${it.message}")
                        }.onSuccess {
                            group.sendMessage("成功启用插件: ${it.name}")
                        }
                    }
                    "disable" -> {
                        kotlin.runCatching {
                            val plugin = HotFixHandler.loadedPlugins.find { it.name == args.getOrNull(1) } ?: return@command let {
                                group.sendMessage(message.quote() + "插件不存在")
                            }
                            HotFixHandler.disable(plugin)
                            plugin
                        }.onFailure {
                            group.sendMessage("禁用插件时发生错误: ${it.message}")
                        }.onSuccess {
                            group.sendMessage("成功禁用插件: ${it.name}")
                        }
                    }
                    "list" -> {
                        group.sendMessage("已加载插件 (JvmPlugin): ")
                        group.sendMessage(HotFixHandler.loadedPlugins.joinToString(separator = "\n") {
                            """
                                ${it.name}
                                author: ${it.author}
                                version: ${it.version}
                                desc: ${it.description.info}
                                
                            """.trimIndent()
                        })
                    }
                    "test" -> {
                        val key = args.getOrNull(1) ?: return@command
                        kotlin.runCatching {
                            Class.forName(key)
                        }.onSuccess {
                            group.sendMessage(message.quote() + "测试结果: 存在 (ClassLoader: ${it.classLoader.name})")
                        }.onFailure {
                            group.sendMessage(message.quote() + "测试结果: 不存在")
                        }
                    }
                    "help" -> {
                        group.sendMessage(
                            """
                                Plugin Manager v1.0.0 by.寒雨
                                
                                Usage: /plugman [args..]
                                
                                Sub:
                                - help              获取帮助信息
                                - test [class]      测试某类是否存在
                                - load [file]       热加载jvm插件
                                - unload [name]     热卸载jvm插件
                                - enable [name]     启用插件
                                - disable [name]    禁用插件
                                - list              列出已加载插件
                            """.trimIndent()
                        )
                    }
                    else -> group.sendMessage(message.quote() + "未知命令捏~")
                }
            }
        }
    }
}

