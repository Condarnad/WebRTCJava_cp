import dev.onvoid.webrtc.RTCIceCandidate
import dev.onvoid.webrtc.RTCSdpType
import dev.onvoid.webrtc.RTCSessionDescription
import org.example.SignallingService
import org.example.SocketClient
import org.json.JSONObject

class APIInteractor(
    private val onMembers: (List<String>) -> Unit
) : SignallingService {

    private var localSessionId: String = ""
    var activeSessionId: String = ""

    private lateinit var sdpEvent: (RTCSessionDescription) -> Unit
    private lateinit var iceEvent: (RTCIceCandidate) -> Unit

    private val socketClient by lazy {
        SocketClient { message ->
            when {
                message.has("members") -> {
                    localSessionId = message.getString("currentSessionId")
                    onMembers(
                        message.getJSONArray("members").toList()
                            .map(Any::toString)
                            .filter { it != localSessionId }
                    )
                }
                message.has("sdp") -> {
                    activeSessionId = message.getString("fromSessionId")
                    val sdp = message.getJSONObject("sdp").getString("sdp")
                    val type = message.getJSONObject("sdp").getString("type")
                    sdpEvent(RTCSessionDescription(RTCSdpType.valueOf(type.toUpperCase()), sdp))

                }
                message.has("candidate") -> {
                    val sdp = message.getJSONObject("candidate").getString("candidate")
                    val sdpMid = message.getJSONObject("candidate").getString("sdpMid")
                    val sdpMLineIndex = message.getJSONObject("candidate").getInt("sdpMLineIndex")
                    iceEvent(RTCIceCandidate(sdpMid, sdpMLineIndex, sdp, ""))
                }
            }
        }
    }

    fun close() {
        socketClient.close()
    }

    fun initSockets() {
        socketClient.connect()
    }

    override fun onIceReceived(listener: (RTCIceCandidate) -> Unit) {
        iceEvent = listener
    }

    override fun onSdpReceived(listener: (RTCSessionDescription) -> Unit) {
        sdpEvent = listener
    }

    override fun sendICE(rtcIceCandidate: RTCIceCandidate) {
        sendMessage(activeSessionId, rtcIceCandidate)
    }

    override fun sendSDP(rtcSessionDescription: RTCSessionDescription) {
        sendMessage(activeSessionId, rtcSessionDescription)
    }

    private fun sendMessage(sessionId: String, message: Any) {
        if (socketClient.isClosed) socketClient.reconnect()
        socketClient.send(prepareMessage(sessionId, message))
    }

    private fun prepareMessage(sessionId: String, message: Any): String {
        val json = JSONObject(mapOf("sessionId" to sessionId, "fromSessionId" to localSessionId))
        when (message) {
            is RTCIceCandidate -> {
                val candidate = JSONObject().apply {
                    put("candidate", message.sdp)
                    put("sdpMid", message.sdpMid)
                    put("sdpMLineIndex", message.sdpMLineIndex)
                }
                json.put("candidate", candidate)
            }
            is RTCSessionDescription -> {
                val sdp = JSONObject().apply {
                    put("sdp", message.sdp);
                    put("type", message.sdpType.toString().toLowerCase());
                }
                json.put("sdp", sdp)
            }
        }

        return json.toString()
    }
}