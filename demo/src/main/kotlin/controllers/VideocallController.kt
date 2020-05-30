package controllers

import DefaultEasyWebRTCClient
import SignallingService
import config.DefaultEasyWebRTCConfig
import dev.onvoid.webrtc.*
import dev.onvoid.webrtc.media.video.VideoFrame
import javafx.application.Platform
import javafx.fxml.FXML
import javafx.fxml.Initializable
import dev.onvoid.webrtc.media.video.VideoTrack
import helpers.Consts
import javafx.scene.image.ImageView
import javafx.stage.Stage
import views.VideoView
import java.net.URL
import java.util.*

class VideocallController : Initializable {

    @FXML
    private lateinit var endCall: ImageView

    @FXML
    private lateinit var muteMic: ImageView

    @FXML
    private lateinit var remoteVideoView: VideoView

    @FXML
    private lateinit var localVideoView: VideoView

    private val defaultEasyWebRTCConfig by lazy {
        object : DefaultEasyWebRTCConfig() {
            override fun onHangup() {
                Platform.runLater {
                    (endCall.scene.window as? Stage)?.close()
                }
            }

            override fun onLocalVideoTrack(remoteVideoTrack: VideoTrack) {
                remoteVideoTrack.addSink { proceedFrame(it, localVideoView::setVideoFrame) }
            }

            override fun onRemoteVideoTrack(remoteVideoTrack: VideoTrack) {
                remoteVideoTrack.addSink { proceedFrame(it, remoteVideoView::setVideoFrame) }
            }

            override fun getRTCConfig(): RTCConfiguration {
                val configuration = RTCConfiguration()
                configuration.iceServers.add(RTCIceServer().apply { urls.add(Consts.GOOGLE_STUN) })
                configuration.iceServers.add(RTCIceServer().apply {
                    urls.add("turn:82.146.49.33:443?transport=tcp")
                    username = "test"
                    password = "test"
                })
                return configuration
            }
        }
    }
    private val webRTCClient by lazy {
        DefaultEasyWebRTCClient(defaultEasyWebRTCConfig, signallingService)
    }

    lateinit var signallingService: SignallingService
    var offerSdp: RTCSessionDescription? = null

    override fun initialize(url: URL?, rb: ResourceBundle?) {
        initUi()
    }

    fun call(){
        if (offerSdp != null) webRTCClient.answerCall(offerSdp!!) else webRTCClient.initCall()
    }

    private fun initUi() {
        endCall.setOnMouseClicked {
            (endCall.scene.window as? Stage)?.close()
        }

        var microphoneEnabled = true
        muteMic.setOnMouseClicked {
            microphoneEnabled = !microphoneEnabled
            webRTCClient.setMicrophoneEnabled(microphoneEnabled)
        }
    }

    fun close() {
        webRTCClient.hangup()
    }

    private fun proceedFrame(frame: VideoFrame, publish: (VideoFrame) -> Unit) {
        frame.retain()
        publish(frame)
        frame.release()
    }
}
