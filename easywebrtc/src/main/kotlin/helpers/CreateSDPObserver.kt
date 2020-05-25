package helpers

import dev.onvoid.webrtc.CreateSessionDescriptionObserver
import dev.onvoid.webrtc.RTCSessionDescription

class CreateSDPObserver(private val onSuccess: (RTCSessionDescription) -> Unit) : CreateSessionDescriptionObserver {
    override fun onSuccess(description: RTCSessionDescription?) {
        description?.let(onSuccess)
    }

    override fun onFailure(error: String?) = Unit
}