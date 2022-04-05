package kim.bifrost.rain.plugman

import kim.bifrost.rain.plugman.utils.ReflectClass
import kim.bifrost.rain.plugman.utils.getProperty
import kim.bifrost.rain.plugman.utils.invokeMethod
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.plugin.Plugin
import net.mamoe.mirai.console.plugin.PluginManager
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginLoader
import net.mamoe.mirai.console.plugin.loader.PluginLoader
import net.mamoe.mirai.console.plugin.name
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.verbose
import java.io.File

/**
 * kim.bifrost.rain.plugman.HotFixHandler
 * plugman
 *
 * @author 寒雨
 * @since 2022/4/5 20:44
 **/
object HotFixHandler {
    fun loadPlugin(jar: File) {
        if (jar.extension == "jar") {
            val loader = PluginManager.builtInLoaders.first { it is JvmPluginLoader } as JvmPluginLoader
            val plugin = loader.invokeMethod<List<JvmPlugin>>("extractPlugins", sequenceOf(jar))!!.first()
            kotlin.runCatching {
                loader.load(plugin)
            }.onSuccess {
                loader.enable(plugin)
                MiraiConsole.mainLogger.verbose("${plugin.name} (${jar.name}) 加载成功")
            }.onFailure {
                MiraiConsole.mainLogger.error({ "${jar.name} 加载失败" }, it)
            }
            return
        }
        MiraiConsole.mainLogger.error("${jar.name} 不是jar文件，无法作为插件加载")
    }

    fun unloadPlugin(name: String, fileName: String = name) {
        val pluginLoaderImpl : PluginManager = ReflectClass
            .find("net.mamoe.mirai.console.internal.plugin.PluginManagerImpl")!!
            .clazz.kotlin.objectInstance as PluginManager
        // 注销插件实例
        val plugin = pluginLoaderImpl.getProperty<MutableList<Plugin>>("resolvedPlugins")!!.run {
            find { it.name == name }!!.also {
                // 禁用
                it.loader.invokeMethod<Unit>("disable", it)
                // 删除
                remove(it)
            }
        } as JvmPlugin
        // 注销PluginLoader
        pluginLoaderImpl.getProperty<MutableList<PluginLoader<*, *>>>("_pluginLoaders")!!.apply {
            // 拿到BuiltInJvmPluginLoaderImpl
            find { it is JvmPluginLoader }!!
                // 拿到pluginClassLoaders
                .getProperty<MutableList<ClassLoader>>("jvmPluginLoadingCtx/pluginClassLoaders")!!
                .apply {
                    // 找到并删除这个插件的pluginClassLoader
                    find {
                        it.name.endsWith("JvmPluginClassLoaderN")
                        && name == plugin.loader.invokeMethod<List<JvmPlugin>>("extractPlugins", sequenceOf(it.getProperty<File>("file")))?.first()?.name
                    }?.also { remove(it) }
                }
        }
        // 垃圾回收
        System.gc()
        MiraiConsole.mainLogger.verbose { "已卸载插件 $name ($fileName)" }
    }
}