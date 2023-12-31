package com.motompro.tcpconfig.app.controller

import com.motompro.tcpconfig.app.TCPConfigApp
import com.motompro.tcpconfig.app.config.Config
import com.motompro.tcpconfig.app.config.ConfigManager
import com.motompro.tcpconfig.app.exception.ApplyConfigException
import javafx.animation.Interpolator
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.event.ActionEvent
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.effect.ColorAdjust
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.text.Text
import javafx.stage.FileChooser
import javafx.util.Duration


class ConfigController {

    var config: Config? = null
        set(value) {
            field = value
            if (value == null) return
            configNameText.text = value.name
            val properties = mutableListOf(value.networkAdapter, value.ip, value.subnetMask)
            if (value.defaultGateway != null) properties.add(value.defaultGateway!!)
            configPropertiesText.text = properties.joinToString("  |  ")
        }
    lateinit var mainController: MainController
    private lateinit var buttons: Set<Button>

    @FXML
    private lateinit var configNameText: Text
    @FXML
    private lateinit var configPropertiesText: Text
    @FXML
    private lateinit var useButton: Button
    @FXML
    private lateinit var editButton: Button
    @FXML
    private lateinit var exportButton: Button
    @FXML
    private lateinit var removeButton: Button

    @FXML
    private fun initialize() {
        buttons = setOf(useButton, editButton, exportButton, removeButton)
        // Set buttons hover animation
        buttons.forEach { button -> MainController.addDarkenEffect(button) }
    }

    @FXML
    private fun onMouseEnterPane(event: MouseEvent) {
        buttons.forEach { it.isVisible = true }
    }

    @FXML
    private fun onMouseExitPane(event: MouseEvent) {
        buttons.forEach { it.isVisible = false }
    }

    @FXML
    private fun onUseButtonClick(event: ActionEvent) {
        if (config == null) {
            TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "Une erreur est survenue")
            return
        }
        try {
            TCPConfigApp.INSTANCE.netInterfaceManager.applyConfig(config!!)
            TCPConfigApp.INSTANCE.showInfoAlert("Succès", "La config a bien été appliquée")
        } catch (ex: ApplyConfigException) {
            when (ex.type) {
                ApplyConfigException.Type.NOT_ENOUGH_ARGS -> TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "La config est corrompue")
                ApplyConfigException.Type.INTERFACE_NOT_CONNECTED -> TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "L'interface ${config!!.networkAdapter} n'est pas connectée à un réseau")
                ApplyConfigException.Type.INTERFACE_NOT_FOUND -> TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "L'interface ${config!!.networkAdapter} n'a pas été trouvée sur cet ordinateur")
            }
        }
    }

    @FXML
    private fun onEditButtonClick(event: ActionEvent) {
        val fxmlLoader = FXMLLoader(TCPConfigApp::class.java.getResource("add-edit-config-view.fxml"))
        val node = fxmlLoader.load<BorderPane>()
        val controller = fxmlLoader.getController<AddEditConfigController>()
        controller.configInEdition = config
        val stage = TCPConfigApp.INSTANCE.stage
        val scene = Scene(node, stage.scene.width, stage.scene.height)
        stage.scene = scene
    }

    @FXML
    private fun onExportButtonClick(event: ActionEvent) {
        val fileChooser = FileChooser()
        fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("Fichiers YAML (*.${ConfigManager.CONFIG_FILE_EXTENSION})", "*.${ConfigManager.CONFIG_FILE_EXTENSION}"))
        val file = fileChooser.showSaveDialog(TCPConfigApp.INSTANCE.stage) ?: return
        TCPConfigApp.INSTANCE.configManager.saveConfig(config!!, file)
        val successAlert = Alert(Alert.AlertType.INFORMATION, "La configuration a bien été exportée dans le fichier ${file.name}")
        successAlert.show()
    }

    @FXML
    private fun onRemoveButtonClick(event: ActionEvent) {
        val confirmAlert = Alert(Alert.AlertType.WARNING, "Voulez-vous vraiment supprimer la config \"${config?.name}\" ?", ButtonType.YES, ButtonType.NO)
        confirmAlert.title = "Attention"
        if (confirmAlert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            config?.let { TCPConfigApp.INSTANCE.configManager.removeConfig(it) }
            mainController.updateConfigList()
        }
    }
}
