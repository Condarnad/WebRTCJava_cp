import dev.onvoid.webrtc.RTCSessionDescription
import views.VideoView

interface EasyWebRTCClient {
    fun initCall()
    fun answerCall(sdp: RTCSessionDescription)
    fun hangup()
    fun setMicrophoneEnabled(isEnabled: Boolean)
    fun setVideoEnabled(isEnabled: Boolean)
}