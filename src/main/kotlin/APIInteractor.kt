package org.example

import org.json.JSONObject

class APIInteractor(
    private val onMembers: (List<String>) -> Unit,
    private val sdpEvent: (String) -> Unit,
    private val iceEvent: (String) -> Unit
) {

    private val socketClient by lazy {
        SocketClient {
            when {
                it.has("members") -> onMembers(it.getJSONArray("members").toList().map(Any::toString))
                it.has("sdp") -> sdpEvent(it.toString())
                it.has("candidates") -> iceEvent(it.toString())
            }
        }
    }

    fun initSockets() {
        socketClient.connect()
    }

    fun sendMessage(sessionId: String, message: String) {
        if (socketClient.isClosed) socketClient.reconnect()
        socketClient.send(prepareMessage(sessionId, message))
    }

    private fun prepareMessage(sessionId: String, message: String) = "{\"sessionId\":\"$sessionId\",$message}"
}