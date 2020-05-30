package helpers

import dev.onvoid.webrtc.*
import dev.onvoid.webrtc.media.MediaStream
import dev.onvoid.webrtc.media.MediaStreamTrack
import dev.onvoid.webrtc.media.video.VideoTrack

class CreatePeerConnectionObserver(
    private val onIceCandidate: (RTCIceCandidate) -> Unit,
    private val onVideoTrack: (VideoTrack) -> Unit,
    private val onHangup: () -> Unit
) : PeerConnectionObserver {

    override fun onRemoveStream(p0: MediaStream?) = Unit
    override fun onRenegotiationNeeded() = Unit

    override fun onTrack(transceiver: RTCRtpTransceiver?) {
        val track = transceiver?.receiver?.track;
        if (track?.kind == MediaStreamTrack.VIDEO_TRACK_KIND) {
            onVideoTrack(track as VideoTrack)
        }
    }

    override fun onConnectionChange(state: RTCPeerConnectionState?) {
        println("DEBUG Connection change: $state")
        if (state == RTCPeerConnectionState.CLOSED) onHangup()
    }

    override fun onSignalingChange(state: RTCSignalingState?) {
        println("DEBUG Signalling change: $state")
        if (state == RTCSignalingState.CLOSED) onHangup()
    }

    override fun onIceConnectionChange(state: RTCIceConnectionState?) {
        println("DEBUG Ice connection change: $state")
        when (state) {
            RTCIceConnectionState.DISCONNECTED, RTCIceConnectionState.FAILED -> onHangup()
        }
    }

    override fun onIceCandidate(ice: RTCIceCandidate?) {
        println("DEBUG Send ice")
        ice?.let(onIceCandidate)
    }
}