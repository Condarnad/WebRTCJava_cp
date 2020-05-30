package controllers

import MainApp
import dev.onvoid.webrtc.RTCSessionDescription
import network.APIInteractor
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.ListView
import javafx.stage.Stage
import java.net.URL
import java.util.*

class MembersController : Initializable {

    @FXML
    private lateinit var members: ListView<String>

    @FXML
    private lateinit var callBtn: Button

    private val apiInteractor by lazy {
        APIInteractor(
            onMembers = {
                Platform.runLater {
                    members.items.setAll(it)
                }
            },
            onIncomingCall = { sessionId: String, rtcSessionDescription: RTCSessionDescription ->
                Platform.runLater {
                    startVideocall(sessionId, rtcSessionDescription)
                }
            }
        )
    }

    override fun initialize(url: URL?, rb: ResourceBundle?) {
        apiInteractor
        callBtn.setOnMouseClicked {
            startVideocall(members.selectionModel.selectedItem)
        }
    }

    private fun startVideocall(sessionId: String, sdp: RTCSessionDescription? = null) {
        apiInteractor.setActiveSessionId(sessionId)

        callBtn.isDisable = true

        val loader = FXMLLoader(MainApp::class.java.getResource("videocall.fxml"))
        val root: Parent = loader.load()
        val scene = Scene(root)
        val stage = Stage()
        stage.minWidth = root.minWidth(-1.0)
        stage.minHeight = root.minHeight(-1.0)
        stage.title = "Videocall"
        stage.scene = scene
        stage.setOnHidden {
            callBtn.isDisable = false
            loader.getController<VideocallController>()?.close()
        }
        loader.getController<VideocallController>()?.signallingService = apiInteractor
        loader.getController<VideocallController>()?.offerSdp = sdp
        stage.show()
        loader.getController<VideocallController>()?.call()
    }

    fun close(){
        apiInteractor.close()
    }
}