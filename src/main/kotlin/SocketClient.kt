package org.example

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONObject
import java.lang.Exception
import java.net.URI

class SocketClient(
    private val onMessage: (JSONObject) -> Unit
) : WebSocketClient(URI.create("ws://82.146.49.33/socket")) {

    override fun onOpen(handshakedata: ServerHandshake?) {}

    override fun onMessage(message: String?) {
        try {
            message?.let { onMessage(JSONObject(message)) }
        } catch (e: Exception) {
        }
    }

    override fun onError(ex: Exception?) {}

    override fun onClose(code: Int, reason: String?, remote: Boolean) {}

}