package network

import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import SignallingService
import org.json.JSONObject

class APIInteractor(
    private val onMembers: (List<String>) -> Unit,
    private val onIncomingCall: (String, RTCSessionDescription) -> Unit
) : SignallingService {

    private var remoteSessionId: String = ""

    private lateinit var sdpEvent: (RTCSessionDescription) -> Unit
    private lateinit var iceEvent: (RTCIceCandidate) -> Unit

    private val socketClient by lazy {
        SocketClient { message ->
            when {
                message.has("members") -> {
                    onMembers(message.getJSONArray("members").toList().map(Any::toString))
                }
                message.has("sdp") -> {
                    remoteSessionId = message.getString("sessionId")
                    val sdp = message.getJSONObject("sdp").getString("description")
                    val type = message.getJSONObject("sdp").getString("type")
                    val description = RTCSessionDescription(RTCSdpType.valueOf(type.toUpperCase()), sdp)
                    if (type == "OFFER") onIncomingCall(remoteSessionId, description) else sdpEvent(description)

                }
                message.has("candidate") -> {
                    val sdp = message.getJSONObject("candidate").getString("sdp")
                    val sdpMid = message.getJSONObject("candidate").getString("sdpMid")
                    val sdpMLineIndex = message.getJSONObject("candidate").getInt("sdpMLineIndex")
                    iceEvent(RTCIceCandidate(sdpMid, sdpMLineIndex, sdp, ""))
                }
            }
        }
    }

    init {
        socketClient.connect()
    }

    fun close() {
        socketClient.close()
    }

    override fun setOnICEReceived(listener: (RTCIceCandidate) -> Unit) {
        iceEvent = listener
    }

    override fun setOnSDPReceived(listener: (RTCSessionDescription) -> Unit) {
        sdpEvent = listener
    }

    override fun sendICE(ice: RTCIceCandidate) {
        sendMessage(remoteSessionId, ice)
    }

    override fun sendSDP(sdp: RTCSessionDescription) {
        sendMessage(remoteSessionId, sdp)
    }

    fun setActiveSessionId(sessionId: String) {
        remoteSessionId = sessionId
    }

    private fun sendMessage(sessionId: String, message: Any) {
        if (socketClient.isClosed) socketClient.reconnect()
        socketClient.send(prepareMessage(sessionId, message))
    }

    private fun prepareMessage(sessionId: String, message: Any): String {
        val json = JSONObject(mapOf("sessionId" to sessionId))
        when (message) {
            is RTCIceCandidate -> {
                val candidate = JSONObject().apply {
                    put("sdp", message.sdp)
                    put("sdpMid", message.sdpMid)
                    put("sdpMLineIndex", message.sdpMLineIndex)
                }
                json.put("candidate", candidate)
            }
            is RTCSessionDescription -> {
                val sdp = JSONObject().apply {
                    put("description", message.sdp);
                    put("type", message.sdpType.toString().toUpperCase());
                }
                json.put("sdp", sdp)
            }
        }

        return json.toString()
    }
}