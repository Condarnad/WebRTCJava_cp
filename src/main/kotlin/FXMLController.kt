package org.example

import javafx.concurrent.Task
import javafx.css.PseudoClass
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.ListView
import org.example.views.VideoView
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class FXMLController : Initializable {

    @FXML
    private lateinit var members: ListView<String>
    @FXML
    private lateinit var call: Button
    @FXML
    private lateinit var remoteVideoView: VideoView
    @FXML
    private lateinit var localVideoView: VideoView

    private val apiInteractor by lazy {
        APIInteractor(
            onMembers = { members.items.setAll(it) },
            sdpEvent = {},
            iceEvent = {}
        )
    }

    private val webRTCWrapper by lazy {
        WebRTCWrapper(apiInteractor).also {
            it.peerConnectionContext.onLocalFrame = Consumer(localVideoView::setVideoFrame)
            it.peerConnectionContext.onRemoteFrame = Consumer(remoteVideoView::setVideoFrame)
        }
    }

    private var currentSessionId: String? = null

    override fun initialize(url: URL?, rb: ResourceBundle?) {
        apiInteractor.initSockets()
        initUi()
        initWebRTC()
    }

    private fun initUi() {
        members.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            call.isDisable = newValue != currentSessionId && currentSessionId != null
        }

        call.isDisable = true
        call.setOnMouseClicked {
            if (call.isDisable) return@setOnMouseClicked
            call.pseudoClassStateChanged(PseudoClass.getPseudoClass("dismiss"), currentSessionId == null)
            if (currentSessionId == null) {
                currentSessionId = members.selectionModel.selectedItem
                members.isDisable = true
                initCall()
            } else {
                members.isDisable = false
                //apiInteractor.dismissCall()
                currentSessionId = null
            }
        }
    }

    private fun initWebRTC() {
        webRTCWrapper
    }

    private fun initCall() {
        CompletableFuture.runAsync {
            webRTCWrapper.initCall(currentSessionId!!)
        }
    }

}