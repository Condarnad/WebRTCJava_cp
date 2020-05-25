import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCSessionDescription

interface SignallingService {
    fun setOnICEReceived(listener: (RTCIceCandidate) -> Unit)
    fun setOnSDPReceived(listener: (RTCSessionDescription) -> Unit)
    fun sendSDP(sdp: RTCSessionDescription)
    fun sendICE(ice: RTCIceCandidate)
}
