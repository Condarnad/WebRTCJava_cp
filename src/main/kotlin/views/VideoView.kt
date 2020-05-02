package org.example.views


import dev.onvoid.webrtc.media.video.VideoFrame
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.value.WritableValue
import javafx.css.StyleableProperty
import javafx.scene.control.Control
import javafx.scene.control.Skin


class VideoView : Control() {
    private val resize: BooleanProperty = SimpleBooleanProperty(true)
    fun setVideoFrame(frame: VideoFrame) {
        val skin = skin as? VideoViewSkin
        skin?.setVideoFrame(frame)
    }

    fun resizeProperty(): BooleanProperty {
        return resize
    }

    fun getResize(): Boolean {
        return resizeProperty().get()
    }

    fun setResize(resize: Boolean) {
        resizeProperty().set(resize)
    }

    override fun createDefaultSkin(): Skin<*> {
        return VideoViewSkin(this)
    }

    override fun getInitialFocusTraversable(): Boolean {
        return java.lang.Boolean.FALSE
    }

    private fun initialize() {
        styleClass.setAll(DEFAULT_STYLE_CLASS)
        val prop =
            focusTraversableProperty() as WritableValue<Boolean?> as StyleableProperty<Boolean?>
        prop.applyStyle(null, java.lang.Boolean.FALSE)
    }

    companion object {
        private const val DEFAULT_STYLE_CLASS = "video-view"
    }

    init {
        initialize()
    }
}