package me.syncrex.goldv2ray.plugin

object PluginContract {

    const val ACTION_NATIVE_PLUGIN = "io.nekohasekai.sagernet.plugin.ACTION_NATIVE_PLUGIN"
    const val EXTRA_ENTRY = "io.nekohasekai.sagernet.plugin.EXTRA_ENTRY"
    const val METADATA_KEY_ID = "io.nekohasekai.sagernet.plugin.id"
    const val METADATA_KEY_EXECUTABLE_PATH = "io.nekohasekai.sagernet.plugin.executable_path"
    const val METHOD_GET_EXECUTABLE = "sagernet:getExecutable"

    const val COLUMN_PATH = "path"
    const val COLUMN_MODE = "mode"
    const val SCHEME = "plugin"
}