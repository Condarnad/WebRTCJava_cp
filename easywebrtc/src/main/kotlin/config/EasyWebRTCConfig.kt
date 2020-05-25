package config

import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.video.VideoCaptureCapability
import dev.onvoid.webrtc.media.video.VideoDevice
import dev.onvoid.webrtc.media.video.VideoFrame
import dev.onvoid.webrtc.media.video.VideoTrack

interface EasyWebRTCConfig {
    fun onRemoteVideoTrack(remoteVideoTrack: VideoTrack)
    fun onLocalVideoTrack(remoteVideoTrack: VideoTrack)
    fun onHangup()

    fun getAudioOptions(): AudioOptions
    fun getVideoDevice(): Pair<VideoDevice, VideoCaptureCapability>

    fun getRTCConfig(): RTCConfiguration
}