package config

import dev.onvoid.webrtc.RTCConfiguration
import dev.onvoid.webrtc.RTCIceServer
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.video.VideoCaptureCapability
import dev.onvoid.webrtc.media.video.VideoDevice
import dev.onvoid.webrtc.media.video.VideoDeviceModule
import dev.onvoid.webrtc.media.video.VideoFrame
import helpers.Consts

abstract class DefaultEasyWebRTCConfig : EasyWebRTCConfig {

    override fun getAudioOptions(): AudioOptions {
        return AudioOptions().apply {
            echoCancellation = true
            noiseSuppression = true
        }
    }

    override fun getVideoDevice(): Pair<VideoDevice, VideoCaptureCapability> {
        val device: VideoDevice = VideoDeviceModule().captureDevices.first()
        val capabilities = VideoDeviceModule().getCaptureCapabilities(device).sortedWith(kotlin.Comparator { a, b ->
            if (a.width == b.width) {
                if (a.height == b.height) {
                    a.frameRate - b.frameRate
                } else {
                    a.height - b.height
                }
            }
            a.width - b.width
        })
        val capability = capabilities.last()
        return device to capability
    }

    override fun getRTCConfig(): RTCConfiguration {
        val configuration = RTCConfiguration()
        configuration.iceServers.add(RTCIceServer().apply { urls.add(Consts.GOOGLE_STUN) })
        return configuration
    }
}