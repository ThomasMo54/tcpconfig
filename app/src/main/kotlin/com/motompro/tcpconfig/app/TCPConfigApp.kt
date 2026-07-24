package com.motompro.tcpconfig.app

import com.google.gson.JsonParser
import com.motompro.tcpconfig.app.component.progressdialog.ProgressDialog
import com.motompro.tcpconfig.app.config.Config
import com.motompro.tcpconfig.app.config.ConfigManager
import com.motompro.tcpconfig.app.controller.MainController
import com.motompro.tcpconfig.app.dhcp.DHCPServer
import com.motompro.tcpconfig.app.netinterfacemanager.NetInterfaceManager
import com.motompro.tcpconfig.app.netinterfacemanager.WindowsNetInterfaceManager
import javafx.application.Application
import javafx.application.Platform
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.image.Image
import javafx.scene.layout.Pane
import javafx.stage.Stage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.lang.Integer.min
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread
import kotlin.system.exitProcess

private const val DEFAULT_WIDTH = 720.0
private const val DEFAULT_HEIGHT = 480.0
private const val MAX_ALERT_MESSAGE_LENGTH = 500

private val GET_LATEST_RELEASE_VERSION_URI = URI.create("https://api.github.com/repos/ThomasMo54/tcpconfig/releases/latest")
private val GET_LATEST_RELEASE_FILE_URL = URL("https://github.com/ThomasMo54/tcpconfig/releases/latest/download/TCPConfig.zip")
private const val FILE_DOWNLOAD_BUFFER_SIZE = 1024

class TCPConfigApp : Application() {

    private val version = readVersion()
    private val defaultTitle = "$WINDOW_TITLE $version"
    private val resources = mutableMapOf<String, URL>()

    val configManager = ConfigManager()
    val netInterfaceManager: NetInterfaceManager = WindowsNetInterfaceManager()
    val dhcpServer = DHCPServer()

    lateinit var stage: Stage
        private set
    lateinit var mainController: MainController
        private set

    var activeConfig: Config? = null
        set(value) {
            field = value
            if (value == null) {
                stage.title = defaultTitle
            } else {
                stage.title = "$defaultTitle - ${value.name}"
            }
        }

    override fun start(stage: Stage) {
        INSTANCE = this
        this.stage = stage

        configManager.loadConfigs()

        val resource = TCPConfigApp::class.java.getResource("main-view.fxml")
        if (resource != null) resources["main-view.fxml"] = resource
        val fxmlLoader = FXMLLoader(resource)
        val scene = Scene(fxmlLoader.load(), DEFAULT_WIDTH, DEFAULT_HEIGHT)
        mainController = fxmlLoader.getController()
        stage.title = defaultTitle
        stage.icons.add(Image(TCPConfigApp::class.java.getResourceAsStream("image/app-icon.png")))
        stage.scene = scene
        stage.isMaximized = true
        stage.show()

        checkForNewerVersion()
    }

    override fun stop() {
        dhcpServer.stop()
        super.stop()
    }

    private fun readVersion(): String {
        val reader = BufferedReader(InputStreamReader(TCPConfigApp::class.java.getResourceAsStream("/version.txt") ?: return "?"))
        val version = reader.readLine().trim()
        reader.close()
        return version
    }

    private fun checkForNewerVersion() {
        getLatestVersion().thenAccept { latestVersion ->
            if (latestVersion == version) return@thenAccept
            Platform.runLater {
                val yesButton = ButtonType("Oui", ButtonBar.ButtonData.YES)
                val noButton = ButtonType("Non", ButtonBar.ButtonData.NO)
                val askUpdateAlert = Alert(
                    Alert.AlertType.INFORMATION,
                    "Une nouvelle version du logiciel est disponible ($latestVersion), voulez-vous l'installer ?",
                    yesButton,
                    noButton,
                )
                askUpdateAlert.title = "Nouvelle version disponible"
                askUpdateAlert.headerText = "Nouvelle version disponible"
                val result = askUpdateAlert.showAndWait()
                if (result.orElse(noButton) == yesButton) {
                    downloadLatestVersion()
                }
            }
        }
    }

    private fun downloadLatestVersion() {
        // Create file
        val fileName = GET_LATEST_RELEASE_FILE_URL.toString().split("/").last()
        val filePath = "tmp${File.separator}$fileName"
        val updateFile = File(filePath)
        updateFile.parentFile.mkdirs()
        updateFile.createNewFile()

        // Open dialog
        val progressDialog = ProgressDialog()
        progressDialog.stage.title = "Téléchargement..."
        progressDialog.show()

        thread(start = true) {
            // Open connection and get file size
            val connection = GET_LATEST_RELEASE_FILE_URL.openConnection() as HttpURLConnection
            val fileSize = connection.contentLength

            // Start download
            val inputStream = BufferedInputStream(connection.inputStream)
            val outputStream = BufferedOutputStream(FileOutputStream(updateFile), FILE_DOWNLOAD_BUFFER_SIZE)
            val data = ByteArray(FILE_DOWNLOAD_BUFFER_SIZE)
            var downloadedSize = 0
            var downloaded: Int
            while (run { downloaded = inputStream.read(data, 0, FILE_DOWNLOAD_BUFFER_SIZE); downloaded } >= 0) {
                downloadedSize += downloaded
                val progress = downloadedSize / fileSize.toDouble()
                Platform.runLater { progressDialog.progress = progress }
                outputStream.write(data, 0, downloaded)
            }
            outputStream.close()
            inputStream.close()

            // Run the updater
            Runtime.getRuntime().exec("cmd /c start updater.exe")

            // Stop app
            exitProcess(0)
        }
    }

    private fun getLatestVersion(): CompletableFuture<String> {
        val request = HttpRequest.newBuilder()
            .uri(GET_LATEST_RELEASE_VERSION_URI)
            .method("GET", HttpRequest.BodyPublishers.noBody())
            .build()
        val completableFuture = CompletableFuture<String>()
        thread(start = true) {
            val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()
            val jsonObject = JsonParser.parseString(response).asJsonObject
            val latestVersion = jsonObject["tag_name"].asString
            completableFuture.complete(latestVersion)
        }
        return completableFuture
    }

    fun showErrorAlert(title: String, message: String) {
        val errorAlert = Alert(Alert.AlertType.ERROR)
        errorAlert.title = title
        errorAlert.headerText = title
        errorAlert.contentText = message.substring(0, min(message.length, MAX_ALERT_MESSAGE_LENGTH))
        errorAlert.dialogPane.minWidth = 500.0
        errorAlert.showAndWait()
    }

    fun showInfoAlert(title: String, message: String) {
        val infoAlert = Alert(Alert.AlertType.INFORMATION)
        infoAlert.title = title
        infoAlert.headerText = title
        infoAlert.contentText = message.substring(0, min(message.length, MAX_ALERT_MESSAGE_LENGTH))
        infoAlert.dialogPane.minWidth = 500.0
        infoAlert.showAndWait()
    }

    fun swapScene(sceneView: String) {
        val fxmlLoader = FXMLLoader(getResourceOrLoad(sceneView))
        val scene = Scene(fxmlLoader.load(), stage.scene.width, stage.scene.height)
        stage.scene = scene
    }

    fun <T> swapScene(sceneView: String): T {
        val fxmlLoader = FXMLLoader(getResourceOrLoad(sceneView))
        val parent = fxmlLoader.load<Parent>()
        val scene = Scene(parent, stage.scene.width, stage.scene.height)
        stage.scene = scene
        return fxmlLoader.getController()
    }

    fun getResourceOrLoad(resourceName: String): URL? {
        if (resources.containsKey(resourceName)) return resources[resourceName]!!
        val resource = TCPConfigApp::class.java.getResource(resourceName) ?: return null
        resources[resourceName] = resource
        return resource
    }

    companion object {
        const val WINDOW_TITLE = "TCPConfig"
        val IP_ADDRESS_REGEX = "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}\$".toRegex()
        val MAC_ADDRESS_REGEX = "^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\$".toRegex()

        lateinit var INSTANCE: TCPConfigApp
            private set
    }
}

fun main() {
    Application.launch(TCPConfigApp::class.java)
}
