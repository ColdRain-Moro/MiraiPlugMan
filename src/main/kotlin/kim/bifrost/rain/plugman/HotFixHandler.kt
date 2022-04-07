package kim.bifrost.rain.plugman

import kim.bifrost.rain.plugman.utils.ReflectClass
import kim.bifrost.rain.plugman.utils.getProperty
import kim.bifrost.rain.plugman.utils.invokeMethod
import net.mamoe.mirai.console.ConsoleFrontEndImplementation
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.MiraiConsoleImplementation
import net.mamoe.mirai.console.plugin.Plugin
import net.mamoe.mirai.console.plugin.PluginManager
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginLoader
import net.mamoe.mirai.console.plugin.name
import net.mamoe.mirai.console.util.cast
import net.mamoe.mirai.utils.error
import net.mamoe.mirai.utils.verbose
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.reflect.full.memberExtensionFunctions
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod

/**
 * kim.bifrost.rain.plugman.HotFixHandler
 * plugman
 *
 * @author 寒雨
 * @since 2022/4/5 20:44
 **/
object HotFixHandler {

    private val pluginManagerImpl: PluginManager by lazy {
        MiraiConsole.pluginManager
    }

    val loadedPlugins: List<JvmPlugin>
        get() = pluginManagerImpl.plugins.filterIsInstance<JvmPlugin>()

    val pluginFolder: File
        get() = pluginManagerImpl.pluginsFolder

    fun enable(plugin: JvmPlugin) {
        pluginManagerImpl.enablePlugin(plugin)
    }

    fun disable(plugin: JvmPlugin) {
        pluginManagerImpl.disablePlugin(plugin)
    }

    @OptIn(ConsoleFrontEndImplementation::class)
    fun loadPlugin(jar: File) {
        if (jar.extension == "jar") {
            val loader = PluginManager.builtInLoaders.first { it is JvmPluginLoader } as JvmPluginLoader
            val plugin = MiraiConsoleImplementation.getInstance().jvmPluginLoader::class
                .memberExtensionFunctions.find { func -> func.name == "extractPlugins" }!!
                .apply { javaMethod!!.isAccessible = true }
                .call(MiraiConsoleImplementation.getInstance().jvmPluginLoader, sequenceOf(jar))
                .cast<List<JvmPlugin>>()
                .first()
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

    @OptIn(ConsoleFrontEndImplementation::class)
    fun unloadPlugin(name: String, delete: Boolean = false) {
        // 注销插件实例
        val plugin = pluginManagerImpl.getProperty<MutableList<Plugin>>("resolvedPlugins")!!.run {
            find { it.name == name }!!.also {
                // 禁用
                it.loader.invokeMethod<Unit>("disable", it)
                // 删除
                remove(it)
            }
        } as JvmPlugin
        // 注销PluginLoader
        // 拿到BuiltInJvmPluginLoaderImpl
        MiraiConsoleImplementation.getInstance().jvmPluginLoader.run {
            javaClass.kotlin.memberProperties
                .find { it.name == "jvmPluginLoadingCtx" }!!
                .getter
                .apply { javaMethod!!.isAccessible = true }
                .invoke(this)!!
        }
            // 调用val的getter最好，因为by lazy不一定初始化了
            // 拿到pluginClassLoaders
            .getProperty<MutableList<ClassLoader>>("pluginClassLoaders")!!
            .apply {
                // 找到并删除这个插件的pluginClassLoader
                first {
                    it.javaClass.name.endsWith("JvmPluginClassLoaderN")
                            && name == MiraiConsoleImplementation.getInstance().jvmPluginLoader.run {
                                javaClass.kotlin.memberProperties
                                    .find { p -> p.name == "pluginFileToInstanceMap" }!!
                                    .apply { javaField?.isAccessible = true }
                                    .get(this)
                                    .cast<ConcurrentHashMap<File, JvmPlugin>>()[it.getProperty<File>("file")]!!
                                    .name
                    }
                }.also {
                    MiraiConsoleImplementation.getInstance().jvmPluginLoader.apply {
                        javaClass.kotlin.memberProperties
                            .find { p -> p.name == "pluginFileToInstanceMap" }!!
                            .apply { javaField?.isAccessible = true }
                            .get(this)
                            .cast<ConcurrentHashMap<File, JvmPlugin>>()
                            .remove(it.getProperty<File>("file"))
                    }
                    // close 之后便可以删除文件
                    (it as? URLClassLoader)?.close()
                    // 删除插件文件
                    if (delete) {
                        it.getProperty<File>("file")?.delete()
                    }
                }
            }
        // 垃圾回收
        System.gc()
        MiraiConsole.mainLogger.verbose { "已卸载插件 $name" }
    }
}