package org.example

import dev.onvoid.webrtc.*
import dev.onvoid.webrtc.media.MediaDevices
import dev.onvoid.webrtc.media.MediaStreamTrack
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.video.*
import org.json.JSONObject
import java.util.Objects.nonNull
import java.util.concurrent.Executors
import java.util.function.BiConsumer
import java.util.function.Consumer


class PeerConnectionContext {
    var audioDirection: RTCRtpTransceiverDirection? = null
    var videoDirection: RTCRtpTransceiverDirection? = null
    var onLocalFrame: Consumer<VideoFrame>? = null
    var onLocalVideoStream: Consumer<Boolean>? = null
    var onStatsReport: Consumer<RTCStatsReport>? = null
    var onRemoteFrame: Consumer<VideoFrame>? = null
    var onRemoteVideoStream: BiConsumer<String, Boolean>? = null
    var onPeerConnectionState: BiConsumer<String, RTCPeerConnectionState>? = null
}

class WebRTCWrapper(private val apiInteractor: APIInteractor) {

    private val executor = Executors.newSingleThreadExecutor()
    private var sessionId: String = ""

    val peerConnectionContext by lazy {
        PeerConnectionContext().apply {
            audioDirection = RTCRtpTransceiverDirection.SEND_RECV
            videoDirection = RTCRtpTransceiverDirection.SEND_RECV
        }
    }

    private val factory by lazy {
        PeerConnectionFactory()
    }

    private val peerConnection: RTCPeerConnection? by lazy {
        factory.createPeerConnection(RTCConfiguration(), object : PeerConnectionObserver {
            override fun onIceCandidate(candidate: RTCIceCandidate?) {
                if (candidate != null && peerConnection != null) {
                    apiInteractor.sendMessage(sessionId, WebRTCConverter.candidatesToJson(candidate))
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out RTCIceCandidate>?) {
                //
            }

            override fun onTrack(transceiver: RTCRtpTransceiver?) {
                val track = transceiver?.receiver?.track;

                if (track?.kind == MediaStreamTrack.VIDEO_TRACK_KIND) {
                    val videoTrack = track as VideoTrack
                    videoTrack.addSink { publishFrame(peerConnectionContext.onRemoteFrame, it) }
                }
            }

            override fun onRemoveTrack(receiver: RTCRtpReceiver?) {
                val track = receiver?.track;
                if (track?.kind == MediaStreamTrack.VIDEO_TRACK_KIND) {

                }
            }

            override fun onConnectionChange(state: RTCPeerConnectionState?) {

            }
        })
    }

    init {
        executeAndWait { peerConnection }
    }

    fun initCall(sessionId: String) {
        initMedia()
        createOffer()
    }

    private fun initMedia() {
        addAudio(peerConnectionContext.audioDirection)
        addVideo(peerConnectionContext.videoDirection)
    }

    private fun addAudio(direction: RTCRtpTransceiverDirection?) {
        if (direction === RTCRtpTransceiverDirection.INACTIVE) return

        val audioOptions = AudioOptions()

        if (direction !== RTCRtpTransceiverDirection.SEND_ONLY) {
            audioOptions.echoCancellation = true
            audioOptions.noiseSuppression = true
        }

        val audioSource = factory.createAudioSource(audioOptions)
        val audioTrack = factory.createAudioTrack("audioTrack", audioSource)

        peerConnection?.addTrack(audioTrack, listOf("stream"))

        for (transceiver in peerConnection!!.transceivers) {
            if (transceiver.sender.track?.kind == MediaStreamTrack.AUDIO_TRACK_KIND) {
                transceiver.direction = direction
                break
            }
        }
    }

    private fun addVideo(direction: RTCRtpTransceiverDirection?) {
        if (direction == RTCRtpTransceiverDirection.INACTIVE) return

        val device: VideoDevice =  MediaDevices.getVideoCaptureDevices().first()
        val capability =             VideoCaptureCapability(480,320,24)
//MediaDevices.getVideoCaptureCapabilities(device).maxBy { it.width }
        val videoSource = VideoDeviceSource()
        if (nonNull(device)) {
            videoSource.setVideoCaptureDevice(device)
        }
        if (nonNull(capability)) {
            videoSource.setVideoCaptureCapability(capability)
        }
        val videoTrack = factory.createVideoTrack("videoTrack", videoSource)
        if (direction == RTCRtpTransceiverDirection.SEND_ONLY ||
            direction == RTCRtpTransceiverDirection.SEND_RECV
        ) {
            val sink = VideoTrackSink { publishFrame(peerConnectionContext.onLocalFrame, it) }
            videoTrack.addSink(sink)
            try {
                videoSource.start()
            }catch (e:java.lang.Exception){
                println("kek")
            }
        }
        peerConnection?.addTrack(videoTrack, listOf("stream"))
        for (transceiver in peerConnection!!.transceivers) {
            if (transceiver.sender.track?.kind == MediaStreamTrack.VIDEO_TRACK_KIND) {
                transceiver.direction = direction
                break
            }
        }
    }

    private fun publishFrame(consumer: Consumer<VideoFrame>?, frame: VideoFrame?) {
        consumer ?: return
        frame ?: return

        frame.retain()
        consumer.accept(frame)
        frame.release()
    }

    private fun createOffer() {
        val options = RTCOfferOptions()
        peerConnection?.createOffer(options, CreateSDObserver())
    }

    private fun createAnswer() {
        val options = RTCAnswerOptions()
        peerConnection?.createAnswer(options, CreateSDObserver())
    }

    private fun setLocalDescription(description: RTCSessionDescription) {
        peerConnection?.setLocalDescription(description, object : SetSessionDescriptionObserver {
            override fun onSuccess() {
                try {
                    apiInteractor.sendMessage(sessionId, WebRTCConverter.sdpToJson(description))
                } catch (e: java.lang.Exception) {
                }
            }

            override fun onFailure(error: String?) {}
        })
    }

    fun setSessionDescription(description: RTCSessionDescription) {
        execute {
            val receivingCall = description.sdpType == RTCSdpType.OFFER
            peerConnection!!.setRemoteDescription(description, object : SetSessionDescriptionObserver {
                override fun onSuccess() {
                    if (receivingCall) {
                        initMedia()
                        createAnswer()
                    }
                }

                override fun onFailure(error: String?) {}
            })
        }
    }

    fun execute(runnable: () -> Unit) {
        executor.execute(runnable)
    }

    private fun executeAndWait(runnable: () -> Unit) {
        try {
            executor.submit(runnable).get()
        } catch (e: Exception) {
        }
    }


    private inner class CreateSDObserver : CreateSessionDescriptionObserver {
        override fun onSuccess(description: RTCSessionDescription) {
            execute { setLocalDescription(description) }
        }

        override fun onFailure(error: String) {}
    }

}

object WebRTCConverter {
    fun candidatesToJson(candidate: RTCIceCandidate): String {
        val body = JSONObject().apply {
            append("candidate", candidate.sdp)
            append("sdpMid", candidate.sdpMid)
            append("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        return "\"candidate\":${body}"
    }

    fun sdpToJson(sdp: RTCSessionDescription): String {
        val body = JSONObject().apply {
            append("sdp", sdp.sdp);
            append("type", sdp.sdpType.toString().toLowerCase());
        }
        return "\"sdp\":${body}"
    }
}