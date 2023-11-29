package com.motompro.tcpconfig.app.config

import com.motompro.tcpconfig.app.TCPConfigApp
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor
import org.yaml.snakeyaml.inspector.TagInspector
import org.yaml.snakeyaml.introspector.BeanAccess
import org.yaml.snakeyaml.introspector.Property
import org.yaml.snakeyaml.introspector.PropertyUtils
import org.yaml.snakeyaml.nodes.Tag
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException
import java.util.Locale


private const val CONFIG_FILE_EXTENSION = "yml"
private const val LEGACY_CONFIG_FILE_EXTENSION = "tcpc"

/**
 * This class manages configs operations such as loading or deleting
 */
class ConfigManager {

    private val _configs = mutableMapOf<String, Config>()
    val configs: Map<String, Config>
        get() = _configs

    private val configsDirectory = File(File(TCPConfigApp::class.java.protectionDomain.codeSource.location.path).parentFile, "configs")
    private val yamlLoader: Yaml

    init {
        val loaderOptions = LoaderOptions()
        loaderOptions.tagInspector = TagInspector { tag: Tag -> tag.getClassName() == Config::class.java.name }
        val constructor = CustomClassLoaderConstructor(javaClass.classLoader, loaderOptions)
        constructor.propertyUtils = PROPERTY_UTILS
        yamlLoader = Yaml(constructor)
        yamlLoader.setBeanAccess(BeanAccess.FIELD)
    }

    /**
     * Load configs from the default configs directory
     */
    fun loadConfigs() {
        if (!configsDirectory.exists()) {
            configsDirectory.mkdirs()
            return
        }
        if (!configsDirectory.isDirectory) {
            throw IllegalStateException()
        }
        configsDirectory.listFiles()
            ?.filter { it.extension == CONFIG_FILE_EXTENSION || it.extension == LEGACY_CONFIG_FILE_EXTENSION }
            ?.forEach { configFile -> loadConfig(configFile) }
    }

    /**
     * Load config from a given config file. If the file is recognized as a legacy one then it will be converted in a
     * correct one
     * @param configFile the config file
     * @return the loaded config data
     */
    fun loadConfig(configFile: File): Config {
        if (configFile.extension == LEGACY_CONFIG_FILE_EXTENSION) {
            val lines = configFile.readLines()
            if (lines.size < 4) {
                TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "Fichier mal formatté (${configFile.name})")
                throw IllegalArgumentException()
            }
            val config = Config(
                lines[0],
                lines[1],
                lines[2],
                lines[3],
                if (lines.size >= 5) lines[4] else null,
                if (lines.size >= 6) lines[5] else null,
                if (lines.size >= 7) lines[6] else null,
            )
            _configs[config.name] = config
            try {
                saveConfig(config)
                configFile.delete()
            } catch (ex: IOException) {
                TCPConfigApp.INSTANCE.showErrorAlert("Erreur", ex.stackTraceToString())
            }
            return config
        }
        if (configFile.extension != CONFIG_FILE_EXTENSION) {
            TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "Fichier non reconnu (${configFile.name})")
            throw IllegalArgumentException()
        }
        val inputStream = FileInputStream(configFile)
        val config = yamlLoader.loadAs(inputStream, Config::class.java)
        _configs[config.name] = config
        inputStream.close()
        return config
    }

    /**
     * Save a config in the given destination file
     * @param config the config's data
     * @param destination *optional* - the destination file
     */
    fun saveConfig(config: Config, destination: File = File(configsDirectory, config.name + ".yml")) {
        destination.createNewFile()
        val fileWriter = FileWriter(destination)
        yamlLoader.dump(config, fileWriter)
        fileWriter.close()
    }

    companion object {
        /**
         * Used to convert properties name that contain dashes to camel case
         */
        private val PROPERTY_UTILS = object : PropertyUtils() {
            override fun getProperty(type: Class<out Any>?, name: String?): Property {
                val formattedName = if (name?.contains('-') == true) {
                    formatToCamelCase(name)
                } else {
                    name
                }
                return super.getProperty(type, formattedName)
            }
        }

        private fun formatToCamelCase(str: String): String {
            val formattedString = str.split('-')
                .filter { it.isNotBlank() }
                .joinToString("") { "${it[0].uppercase(Locale.ENGLISH)}${it.substring(1)}" }
            return "${formattedString[0].lowercase(Locale.ENGLISH)}${formattedString.substring(1)}"
        }
    }
}