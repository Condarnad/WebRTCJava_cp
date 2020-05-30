package helpers

import dev.onvoid.webrtc.SetSessionDescriptionObserver

class SetSDPObserver(private val onSuccessListener: () -> Unit) : SetSessionDescriptionObserver {

    override fun onFailure(error: String?) = Unit
    override fun onSuccess() {
        onSuccessListener()
    }
}