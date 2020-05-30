import helpers.CreatePeerConnectionObserver
import config.EasyWebRTCConfig
import dev.onvoid.webrtc.*
import dev.onvoid.webrtc.media.MediaStream
import dev.onvoid.webrtc.media.MediaStreamTrack
import dev.onvoid.webrtc.media.audio.AudioSource
import dev.onvoid.webrtc.media.audio.AudioTrack
import dev.onvoid.webrtc.media.video.*
import helpers.CreateSDPObserver
import helpers.SetSDPObserver
import java.util.concurrent.Executors

class DefaultEasyWebRTCClient(
    private val easyWebRTCConfig: EasyWebRTCConfig,
    private val signallingService: SignallingService
) : EasyWebRTCClient {

    private var executor = Executors.newSingleThreadExecutor()

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoDeviceSource? = null
    private var audioSource: AudioSource? = null

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: RTCPeerConnection? = null

    init {
        signallingService.setOnICEReceived {
            println("DEBUG New ice received")
            peerConnection?.addIceCandidate(it)
        }
        signallingService.setOnSDPReceived {
            println("DEBUG New sdp received")
            if (it.sdpType != RTCSdpType.OFFER) {
                peerConnection?.setRemoteDescription(it, SetSDPObserver {
                    println("DEBUG Remote sdp set")
                })
            }
        }
    }

    override fun initCall() {
        executor.submit {
            createPeerConnection()
            initMedia()
            createOffer()
        }.get()
    }

    override fun answerCall(sdp: RTCSessionDescription) {
        if (sdp.sdpType != RTCSdpType.OFFER) return

        executor.execute {
            createPeerConnection()
            initMedia()
            peerConnection?.setRemoteDescription(
                sdp,
                SetSDPObserver {
                    println("DEBUG Remote sdp set")
                    createAnswer()
                }
            )
        }
    }

    override fun setVideoEnabled(isEnabled: Boolean) {
        localVideoTrack?.isEnabled = isEnabled
    }

    override fun setMicrophoneEnabled(isEnabled: Boolean) {
        localAudioTrack?.isEnabled = isEnabled
    }

    private fun createPeerConnection() {
        peerConnectionFactory = PeerConnectionFactory()

        val observer = CreatePeerConnectionObserver(
            onIceCandidate = signallingService::sendICE,
            onVideoTrack = easyWebRTCConfig::onRemoteVideoTrack,
            onHangup = this::hangup
        )
        peerConnection = peerConnectionFactory?.createPeerConnection(easyWebRTCConfig.getRTCConfig(), observer)
    }

    private fun initMedia() {
        addAudio()
        addVideo()
    }

    private fun addAudio() {
        audioSource = peerConnectionFactory?.createAudioSource(easyWebRTCConfig.getAudioOptions())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audioTrack", audioSource)

        peerConnection?.addTrack(localAudioTrack, listOf("stream"))

        for (transceiver in peerConnection!!.transceivers) {
            if (transceiver.sender.track?.kind == MediaStreamTrack.AUDIO_TRACK_KIND) {
                transceiver.direction = RTCRtpTransceiverDirection.SEND_RECV
                break
            }
        }
    }

    private fun addVideo() {
        val (device, capability) = easyWebRTCConfig.getVideoDevice()

        videoSource = VideoDeviceSource().apply {
            setVideoCaptureDevice(device)
            setVideoCaptureCapability(capability)
        }

        localVideoTrack = peerConnectionFactory?.createVideoTrack("videoTrack", videoSource)
        try {
            easyWebRTCConfig.onLocalVideoTrack(localVideoTrack!!)
            videoSource?.start()
        } catch (e: java.lang.Exception) {
        }

        peerConnection?.addTrack(localVideoTrack, listOf("stream"))
        for (transceiver in peerConnection!!.transceivers) {
            if (transceiver.sender.track?.kind == MediaStreamTrack.VIDEO_TRACK_KIND) {
                transceiver.direction = RTCRtpTransceiverDirection.SEND_RECV
                break
            }
        }
    }

    private fun createOffer() {
        peerConnection?.createOffer(
            RTCOfferOptions(),
            CreateSDPObserver {
                println("DEBUG Offer sdp created")
                executor.execute { setLocalDescription(it) }
            }
        )
    }

    private fun createAnswer() {
        peerConnection?.createAnswer(
            RTCAnswerOptions(),
            CreateSDPObserver {
                println("DEBUG Answer created")
                executor.execute { setLocalDescription(it) }
            }
        )
    }

    private fun setLocalDescription(description: RTCSessionDescription) {
        peerConnection?.setLocalDescription(
            description,
            SetSDPObserver {
                println("DEBUG Local sdp set")
                signallingService.sendSDP(description)
            }
        )
    }

    override fun hangup() {
        if (executor.isTerminated) executor = Executors.newSingleThreadExecutor()
        executor.execute {
            videoSource?.stop()
            videoSource?.dispose()
            videoSource = null

            peerConnection?.close()
            peerConnection = null

            peerConnectionFactory?.dispose()
            peerConnectionFactory = null

            easyWebRTCConfig.onHangup()

            executor.shutdownNow()
        }
    }
}