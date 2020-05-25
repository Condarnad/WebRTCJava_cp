package org.example

import APIInteractor
import dev.onvoid.webrtc.RTCPeerConnectionState
import dev.onvoid.webrtc.media.video.VideoFrame
import javafx.application.Platform
import javafx.css.PseudoClass
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListView
import org.example.views.VideoView
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import kotlin.system.exitProcess

class FXMLController : WebRTCHooks(), Initializable {

    @FXML
    private lateinit var members: ListView<String>

    @FXML
    private lateinit var call: Button

    @FXML
    private lateinit var label: Label

    @FXML
    private lateinit var remoteVideoView: VideoView

    @FXML
    private lateinit var localVideoView: VideoView

    private val webRTCContainer by lazy {
        WebRTCContainer(this, apiInteractor)
    }
    private var localSessionId: String? = null
    private var remoteSessionId: String? = null

    private val apiInteractor by lazy {
        APIInteractor {
            Platform.runLater {
                members.items.setAll(it)
            }
        }
    }

    override fun initialize(url: URL?, rb: ResourceBundle?) {
        apiInteractor.initSockets()
        initUi()
        webRTCContainer
    }

    private fun initUi() {
        members.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
            call.isDisable = remoteSessionId == null && newValue.isEmpty()
        }
        call.isDisable = true
        call.setOnMouseClicked {
            if (call.isDisable) return@setOnMouseClicked
            call.pseudoClassStateChanged(PseudoClass.getPseudoClass("dismiss"), remoteSessionId == null)
            if (remoteSessionId == null) {
                remoteSessionId = members.selectionModel.selectedItem
                members.isDisable = true
                initCall()
                call.text = "СБРОСИТЬ"
            } else {
                call.text = "ПОЗВОНИТЬ"
                members.isDisable = false
                dismissCall()
                remoteSessionId = null
            }
        }
    }

    override fun onLocalFrame(frame: VideoFrame) {
        localVideoView.setVideoFrame(frame)
    }

    override fun onRemoteFrame(frame: VideoFrame) {
        remoteVideoView.setVideoFrame(frame)
    }

    private fun initCall() {
        apiInteractor.activeSessionId = remoteSessionId!!
        webRTCContainer.call()
    }

    override fun onConnectionChange(state: RTCPeerConnectionState) {
        Platform.runLater {
            label.text = when (state) {
                RTCPeerConnectionState.CLOSED -> "Соединение закрыто"
                RTCPeerConnectionState.DISCONNECTED -> "Отключено"
                RTCPeerConnectionState.CONNECTED -> "Соединение установлено"
                RTCPeerConnectionState.CONNECTING -> "Установка соединения"
                RTCPeerConnectionState.FAILED -> "Ошибка"
                RTCPeerConnectionState.NEW -> "Создание соединения"
            }
            val isVisible = state != RTCPeerConnectionState.FAILED && state != RTCPeerConnectionState.DISCONNECTED && state != RTCPeerConnectionState.CLOSED
            remoteVideoView.isVisible = isVisible
            localVideoView.isVisible = isVisible
        }
    }

    fun close() {
        dismissCall()
        apiInteractor.close()
        Platform.exit()
    }

    private fun dismissCall() {
        webRTCContainer.destroy()
    }

}
