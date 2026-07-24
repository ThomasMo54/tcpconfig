package com.motompro.tcpconfig.app.controller

import com.motompro.tcpconfig.app.TCPConfigApp
import com.motompro.tcpconfig.app.component.RangeComponent
import com.motompro.tcpconfig.app.component.draggabletab.DraggableTab
import com.motompro.tcpconfig.app.dhcp.DHCPServerListener
import com.motompro.tcpconfig.app.dhcp.history.AddressAssignHistory
import com.motompro.tcpconfig.app.dhcp.history.ServerStartHistory
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import java.io.IOException

class DHCPController : DHCPServerListener {

    private val serverStartedIcon = Image(TCPConfigApp::class.java.getResourceAsStream("image/power-icon-enabled.png"))
    private val serverStoppedIcon = Image(TCPConfigApp::class.java.getResourceAsStream("image/power-icon.png"))

    @FXML
    private lateinit var backButton: Button
    @FXML
    private lateinit var netInterfaceComboBox: ComboBox<String>
    @FXML
    private lateinit var startButton: Button
    @FXML
    private lateinit var startButtonImage: ImageView
    @FXML
    private lateinit var historyVBox: VBox
    @FXML
    private lateinit var historyListView: ListView<String>
    @FXML
    private lateinit var reservationsVBox: VBox
    @FXML
    private lateinit var reservationMacTextField: TextField
    @FXML
    private lateinit var reservationIpTextField: TextField
    @FXML
    private lateinit var addReservationButton: Button
    @FXML
    private lateinit var removeReservationButton: Button
    @FXML
    private lateinit var reservationsListView: ListView<String>

    private lateinit var ipRangeComponent: RangeComponent
    lateinit var tab: DraggableTab

    @FXML
    private fun initialize() {
        historyListView.prefHeightProperty().bind((historyVBox.parent as HBox).heightProperty())
        historyVBox.prefHeightProperty().bind((historyVBox.parent as HBox).heightProperty())
        reservationsVBox.prefHeightProperty().bind((historyVBox.parent as HBox).heightProperty())

        // Add range component
        val fxmlLoader = FXMLLoader(TCPConfigApp::class.java.getResource("range-component.fxml"))
        val node = fxmlLoader.load<HBox>()
        ipRangeComponent = fxmlLoader.getController()
        (netInterfaceComboBox.parent as VBox).children.add(3, node)

        // Add network interfaces
        try {
            val netInterfaces = TCPConfigApp.INSTANCE.netInterfaceManager.netInterfaces.sorted()
            netInterfaceComboBox.items.addAll(netInterfaces)
            netInterfaceComboBox.selectionModel.selectFirst()
        } catch (ex: IOException) {
            TCPConfigApp.INSTANCE.showErrorAlert("Erreur", ex.stackTraceToString())
        }

        // Set current values
        val dhcpServer = TCPConfigApp.INSTANCE.dhcpServer
        dhcpServer.listener = this
        if (dhcpServer.networkAdapter != null) netInterfaceComboBox.value = dhcpServer.networkAdapter
        if (dhcpServer.ipRange != null) {
            ipRangeComponent.rangeStartTextField.text = dhcpServer.ipRange!!.startIP
            ipRangeComponent.rangeEndTextField.text = dhcpServer.ipRange!!.endIP
        }
        dhcpServer.history.map { it.toMessage() }.forEach {
            historyListView.items.add(it)
        }
        historyListView.scrollTo(historyListView.items.size - 1)
        refreshReservationsListView()
        if (dhcpServer.isStarted) {
            startButtonImage.image = serverStartedIcon
            startButton.text = "Arrêter"
            setInputsDisabled(true)
        }
    }

    @FXML
    private fun onBackButtonClick() {
        TCPConfigApp.INSTANCE.swapScene("tcp-view.fxml")
    }

    @FXML
    private fun onStartButtonClick() {
        val dhcpServer = TCPConfigApp.INSTANCE.dhcpServer

        if (dhcpServer.isStarted) {
            dhcpServer.stop()
            startButtonImage.image = serverStoppedIcon
            startButton.text = "Démarrer"
            setInputsDisabled(false)
            TCPConfigApp.INSTANCE.showInfoAlert("Serveur DHCP", "Le serveur DHCP a bien été arrêté")
            return
        }

        if (!ipRangeComponent.range.isValid) {
            TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "La plage contient une adresse mal formatée.")
            return
        }

        val netInterface = netInterfaceComboBox.value
        if (!TCPConfigApp.INSTANCE.netInterfaceManager.interfaceHasStaticIP(netInterface)) {
            TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "Impossible de démarrer le serveur sur une interface réseau en DHCP")
            return
        }

        historyListView.items.clear()
        try {
            dhcpServer.start(netInterface, ipRangeComponent.range)
        } catch (ex: Exception) {
            TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "Impossible de démarrer le serveur DHCP sur cette interface réseau")
            return
        }
        startButtonImage.image = serverStartedIcon
        startButton.text = "Arrêter"
        setInputsDisabled(true)
        TCPConfigApp.INSTANCE.showInfoAlert("Serveur DHCP", "Le serveur DHCP a bien été démarré")
    }

    @FXML
    private fun onAddReservationButtonClick() {
        val macAddress = reservationMacTextField.text.trim()
        val ipAddress = reservationIpTextField.text.trim()

        if (!macAddress.matches(TCPConfigApp.MAC_ADDRESS_REGEX)) {
            TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "L'adresse MAC est mal formatée.")
            return
        }
        if (!ipAddress.matches(TCPConfigApp.IP_ADDRESS_REGEX)) {
            TCPConfigApp.INSTANCE.showErrorAlert("Erreur", "L'adresse IP est mal formatée.")
            return
        }

        TCPConfigApp.INSTANCE.dhcpServer.addReservation(macAddress, ipAddress)
        reservationMacTextField.clear()
        reservationIpTextField.clear()
        refreshReservationsListView()
    }

    @FXML
    private fun onRemoveReservationButtonClick() {
        val selectedItem = reservationsListView.selectionModel.selectedItem ?: return
        val macAddress = selectedItem.substringBefore(" -> ")
        TCPConfigApp.INSTANCE.dhcpServer.removeReservation(macAddress)
        refreshReservationsListView()
    }

    private fun refreshReservationsListView() {
        reservationsListView.items.setAll(
            TCPConfigApp.INSTANCE.dhcpServer.reservations.map { (macAddress, ipAddress) -> "${macAddress.chunked(2).joinToString(":")} -> $ipAddress" }
        )
    }

    private fun setInputsDisabled(disabled: Boolean) {
        netInterfaceComboBox.isDisable = disabled
        ipRangeComponent.rangeStartTextField.isDisable = disabled
        ipRangeComponent.rangeEndTextField.isDisable = disabled
        reservationMacTextField.isDisable = disabled
        reservationIpTextField.isDisable = disabled
        addReservationButton.isDisable = disabled
        removeReservationButton.isDisable = disabled
    }

    override fun onServerStart(history: ServerStartHistory) {
        Platform.runLater { historyListView.items.add(history.toMessage()) }
    }

    override fun onAddressAssign(history: AddressAssignHistory) {
        Platform.runLater { historyListView.items.add(history.toMessage()) }
    }
}
