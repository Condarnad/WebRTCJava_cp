package org.example

import dev.onvoid.webrtc.*
import dev.onvoid.webrtc.media.MediaStreamTrack
import dev.onvoid.webrtc.media.audio.AudioOptions
import dev.onvoid.webrtc.media.audio.AudioSource
import dev.onvoid.webrtc.media.video.*
import java.util.*
import java.util.concurrent.Executors

class WebRTCContainer(
    private val webRTCHooks: WebRTCHooks,
    private val signallingService: SignallingService
) : PeerConnectionObserver {

    private var executor = Executors.newSingleThreadExecutor()

    private var videoSource: VideoDeviceSource? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: RTCPeerConnection? = null

    init {
        signallingService.onIceReceived {
            peerConnection?.addIceCandidate(it)
        }
        signallingService.onSdpReceived {
            setRemoteDescription(it)
        }
    }

    fun call() {
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }
        executeAndWait {
            peerConnectionFactory = PeerConnectionFactory()
            peerConnection = peerConnectionFactory?.createPeerConnection(webRTCHooks.getConfiguration(), this)
            initMedia()
            createOffer()
        }
    }

    private fun initMedia() {
        addAudio(webRTCHooks.getAudioDirection())
        addVideo(webRTCHooks.getVideoDirection())
    }

    private fun addAudio(direction: RTCRtpTransceiverDirection?) {
        if (direction == RTCRtpTransceiverDirection.INACTIVE) return

        val audioOptions = AudioOptions()

        if (direction != RTCRtpTransceiverDirection.SEND_ONLY) {
            audioOptions.echoCancellation = true
            audioOptions.noiseSuppression = true
        }

        val audioSource = peerConnectionFactory?.createAudioSource(audioOptions)
        val audioTrack = peerConnectionFactory?.createAudioTrack("audioTrack", audioSource)

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

        val (device, capability) = webRTCHooks.getVideoDevice()

        videoSource = VideoDeviceSource()
        videoSource?.setVideoCaptureDevice(device)
        videoSource?.setVideoCaptureCapability(capability)

        val videoTrack = peerConnectionFactory?.createVideoTrack("videoTrack", videoSource)
        if (direction == RTCRtpTransceiverDirection.SEND_ONLY || direction == RTCRtpTransceiverDirection.SEND_RECV) {
            val sink = VideoTrackSink { proceedFrame(it, webRTCHooks::onLocalFrame) }
            videoTrack?.addSink(sink)
            try {
                videoSource?.start()
            } catch (e: java.lang.Exception) {
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

    override fun onIceCandidate(candidate: RTCIceCandidate?) {
        if (candidate != null && peerConnection != null) {
            signallingService.sendICE(candidate)
        }
    }

    override fun onTrack(transceiver: RTCRtpTransceiver?) {
        val track = transceiver?.receiver?.track;

        if (track?.kind == MediaStreamTrack.VIDEO_TRACK_KIND) {
            val videoTrack = track as VideoTrack
            videoTrack.addSink { proceedFrame(it, webRTCHooks::onRemoteFrame) }
        }
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
                    signallingService.sendSDP(description)
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(error: String?) {}
        })
    }

    private fun setRemoteDescription(description: RTCSessionDescription) {
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }
        execute {
            val receivingCall = description.sdpType == RTCSdpType.OFFER
            if(receivingCall){
                peerConnectionFactory = PeerConnectionFactory()
                peerConnection = peerConnectionFactory?.createPeerConnection(webRTCHooks.getConfiguration(), this)
                initMedia()
            }
            peerConnection!!.setRemoteDescription(description, object : SetSessionDescriptionObserver {
                override fun onSuccess() {
                    if (receivingCall) {
                        createAnswer()
                    }
                }

                override fun onFailure(error: String?) {}
            })
        }
    }

    private fun proceedFrame(frame: VideoFrame, publish: (VideoFrame) -> Unit) {
        frame.retain()
        publish(frame)
        frame.release()
    }

    private fun execute(runnable: () -> Unit) {
        executor.execute(runnable)
    }

    private fun executeAndWait(runnable: () -> Unit) {
        try {
            executor.submit(runnable).get()
        } catch (e: Exception) {
        }
    }

    fun destroy() {
        execute {
            videoSource?.stop()
            videoSource?.dispose()
            videoSource = null

            peerConnection?.close()
            peerConnection = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
            executor.shutdownNow()
        }
    }

    private inner class CreateSDObserver : CreateSessionDescriptionObserver {
        override fun onSuccess(description: RTCSessionDescription) {
           setLocalDescription(description)
        }

        override fun onFailure(error: String) = Unit
    }

    override fun onConnectionChange(state: RTCPeerConnectionState?) {
       state?.let(webRTCHooks::onConnectionChange)
    }
}


interface SignallingService {
    fun sendSDP(rtcSessionDescription: RTCSessionDescription)
    fun sendICE(rtcIceCandidate: RTCIceCandidate)
    fun onIceReceived(listener: (RTCIceCandidate) -> Unit)
    fun onSdpReceived(listener: (RTCSessionDescription) -> Unit)
}

abstract class WebRTCHooks {

    fun getConfiguration(): RTCConfiguration {
        val configuration = RTCConfiguration()
        val stun = RTCIceServer().apply { urls.add("stun:82.146.49.33:443") }
        val turn = RTCIceServer().apply {
            urls.add("turn:82.146.49.33:443?transport=tcp")
            username="test"
            password="test"
        }
        val turn2 = RTCIceServer().apply {
            urls.add("turn:82.146.49.33:443?transport=udp")
            username="test"
            password="test"
        }
        configuration.iceServers.add(stun)
        configuration.iceServers.add(turn)
        configuration.iceServers.add(turn2)

        return configuration
    }

    fun getVideoDevice(): Pair<VideoDevice, VideoCaptureCapability> {
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

    open fun onConnectionChange(state: RTCPeerConnectionState) = Unit
    fun getVideoDirection() = RTCRtpTransceiverDirection.SEND_RECV
    fun getAudioDirection() = RTCRtpTransceiverDirection.SEND_RECV

    abstract fun onRemoteFrame(frame: VideoFrame)
    abstract fun onLocalFrame(frame: VideoFrame)
}