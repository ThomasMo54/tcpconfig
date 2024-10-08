package com.motompro.tcpconfig.app.controller

import com.motompro.tcpconfig.app.TCPConfigApp
import com.motompro.tcpconfig.app.component.draggabletab.DraggableTab
import com.motompro.tcpconfig.app.config.Config
import com.motompro.tcpconfig.app.exception.InvalidConfigFieldException
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.TextField
import java.io.IOException

private const val CONFIG_NAME_MAX_LENGTH = 50

class AddEditConfigController {

    var configInEdition: Config? = null
        set(value) {
            field = value
            setExistingFields()
        }
    lateinit var tab: DraggableTab

    @FXML
    private lateinit var backButton: Button
    @FXML
    private lateinit var nameTextField: TextField
    @FXML
    private lateinit var netInterfaceComboBox: ComboBox<String>
    @FXML
    private lateinit var ipTextField: TextField
    @FXML
    private lateinit var maskTextField: TextField
    @FXML
    private lateinit var gatewayTextField: TextField
    @FXML
    private lateinit var favDNSTextField: TextField
    @FXML
    private lateinit var auxDNSTextField: TextField

    @FXML
    private fun initialize() {
        try {
            val netInterfaces = TCPConfigApp.INSTANCE.netInterfaceManager.netInterfaces.sorted()
            netInterfaceComboBox.items.addAll(netInterfaces)
            netInterfaceComboBox.selectionModel.selectFirst()
        } catch (ex: IOException) {
            TCPConfigApp.INSTANCE.showErrorAlert("Erreur", ex.stackTraceToString())
        }
    }

    @FXML
    private fun onBackButtonClick() {
        TCPConfigApp.INSTANCE.swapScene("tcp-view.fxml")
    }

    @FXML
    private fun onValidateButtonClick() {
        try {
            validateFields()
            if (configInEdition != null) {
                TCPConfigApp.INSTANCE.configManager.removeConfig(configInEdition!!)
                configInEdition!!.name = nameTextField.text
                configInEdition!!.networkAdapter = netInterfaceComboBox.value
                configInEdition!!.ip = ipTextField.text
                configInEdition!!.subnetMask = maskTextField.text
                configInEdition!!.defaultGateway = gatewayTextField.text.ifBlank { null }
                configInEdition!!.preferredDNS = favDNSTextField.text.ifBlank { null }
                configInEdition!!.auxDNS = auxDNSTextField.text.ifBlank { null }
                TCPConfigApp.INSTANCE.configManager.addConfig(configInEdition!!)
            } else {
                val config = Config(
                    nameTextField.text,
                    netInterfaceComboBox.value,
                    ipTextField.text,
                    maskTextField.text,
                    gatewayTextField.text.ifBlank { null },
                    favDNSTextField.text.ifBlank { null },
                    auxDNSTextField.text.ifBlank { null },
                )
                TCPConfigApp.INSTANCE.configManager.addConfig(config)
            }
            val mainController = TCPConfigApp.INSTANCE.mainController
            mainController.tcpController?.updateConfigList()
            mainController.closeTab(tab)
            mainController.focusTCPTab()
        } catch (ex: InvalidConfigFieldException) {
            when (ex.type) {
                InvalidConfigFieldException.Type.NAME_TOO_LONG -> {
                    TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "Le nom doit avoir une longueur de $CONFIG_NAME_MAX_LENGTH caractères max.")
                }
                InvalidConfigFieldException.Type.BAD_IP_FORMAT -> {
                    TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "Le champ \"${ex.fieldName}\" contient une adresse IP mal formatée")
                }
                InvalidConfigFieldException.Type.MISSING_DNS_FIELD -> {
                    TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "Les 2 champs DNS doivent être remplis")
                }
                InvalidConfigFieldException.Type.MISSING_MANDATORY_FIELD -> {
                    TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "Le champ \"${ex.fieldName}\" est obligatoire")
                }
            }
        }
    }

    private fun setExistingFields() {
        configInEdition!!.let { config ->
            nameTextField.text = config.name
            netInterfaceComboBox.selectionModel.select(config.networkAdapter)
            ipTextField.text = config.ip
            maskTextField.text = config.subnetMask
            if (config.defaultGateway != null) gatewayTextField.text = config.defaultGateway
            if (config.preferredDNS != null) favDNSTextField.text = config.preferredDNS
            if (config.auxDNS != null) auxDNSTextField.text = config.auxDNS
        }
    }

    private fun validateFields() {
        val mandatoryFields = mapOf(
            nameTextField to "Nom",
            ipTextField to "Adresse IP",
            maskTextField to "Masque de sous-réseau",
        )
        mandatoryFields.filter { it.key.text.isBlank() }.forEach {
            throw InvalidConfigFieldException(InvalidConfigFieldException.Type.MISSING_MANDATORY_FIELD, it.value)
        }
        if (nameTextField.text.length > CONFIG_NAME_MAX_LENGTH) {
            throw InvalidConfigFieldException(InvalidConfigFieldException.Type.NAME_TOO_LONG)
        }
        if (netInterfaceComboBox.value == null || netInterfaceComboBox.value.isBlank()) {
            throw InvalidConfigFieldException(InvalidConfigFieldException.Type.MISSING_MANDATORY_FIELD, "Interface réseau")
        }
        val ipFields = mapOf(
            ipTextField to "Adresse IP",
            maskTextField to "Masque de sous-réseau",
            gatewayTextField to "Passerelle par défaut",
            favDNSTextField to "DNS Favori",
            auxDNSTextField to "DNS Auxiliaire",
        )
        ipFields.filter { it.key.text.isNotBlank() }.forEach {
            if (!TCPConfigApp.IP_ADDRESS_REGEX.matches(it.key.text)) throw InvalidConfigFieldException(InvalidConfigFieldException.Type.BAD_IP_FORMAT, it.value)
        }
        if ((favDNSTextField.text.isNotBlank() && auxDNSTextField.text.isBlank()) || favDNSTextField.text.isBlank() && auxDNSTextField.text.isNotBlank()) {
            throw InvalidConfigFieldException(InvalidConfigFieldException.Type.MISSING_DNS_FIELD)
        }
    }
}
