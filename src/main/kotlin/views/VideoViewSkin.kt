package org.example.views

import dev.onvoid.webrtc.media.FourCC
import dev.onvoid.webrtc.media.video.VideoBufferConverter
import dev.onvoid.webrtc.media.video.VideoFrame
import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Bounds
import javafx.scene.control.SkinBase
import javafx.scene.image.ImageView
import javafx.scene.image.PixelBuffer
import javafx.scene.image.WritableImage
import javafx.scene.image.WritablePixelFormat
import javafx.scene.layout.Region
import javafx.scene.shape.Rectangle
import java.awt.image.*
import java.nio.ByteBuffer
import java.util.*


class VideoViewSkin(control: VideoView) : SkinBase<VideoView?>(control) {
    private var imageView: ImageView? = null
    private var pixelBuffer: PixelBuffer<ByteBuffer?>? = null
    private var byteBuffer: ByteBuffer? = null
    private var border: Rectangle? = null
    private var controlBoundsListener: ChangeListener<Bounds>? = null
    private var imageBoundsListener: ChangeListener<Bounds>? = null
    override fun dispose() {
        val control = skinnable
        unregisterChangeListeners(control!!.resizeProperty())
        if (Objects.nonNull(controlBoundsListener)) {
            control.layoutBoundsProperty().removeListener(controlBoundsListener)
        }
        super.dispose()
    }

    private fun createBufferedImage(pixels: ByteArray, width: Int, height: Int): BufferedImage? {
        val sm = getIndexSampleModel(width, height)
        val db: DataBuffer = DataBufferByte(pixels, width * height, 0)
        val raster = Raster.createWritableRaster(sm, db, null)
        val cm: IndexColorModel = getDefaultColorModel()
        return BufferedImage(cm, raster, false, null)
    }


    private fun getIndexSampleModel(width: Int, height: Int): SampleModel? {
        val icm = getDefaultColorModel()
        val wr = icm.createCompatibleWritableRaster(1, 1)
        var sampleModel = wr.sampleModel
        sampleModel = sampleModel.createCompatibleSampleModel(width, height)
        return sampleModel
    }

    private fun getDefaultColorModel(): IndexColorModel {
        val r = ByteArray(256)
        val g = ByteArray(256)
        val b = ByteArray(256)
        for (i in 0..255) {
            r[i] = i.toByte()
            g[i] = i.toByte()
            b[i] = i.toByte()
        }
        return IndexColorModel(8, 256, r, g, b)
    }


    fun setVideoFrame(frame: VideoFrame) {
        val buffer = frame.buffer
        val width = buffer.width
        val height = buffer.height
        if (Objects.isNull(pixelBuffer) || pixelBuffer!!.width != width || pixelBuffer!!.height != height) {
            byteBuffer = ByteBuffer.allocate(width * height * 4)
            pixelBuffer = PixelBuffer(
                width,
                height,
                byteBuffer,
                WritablePixelFormat.getByteBgraPreInstance()
            )
            imageView!!.image = WritableImage(width, height)
        }

        try {
            VideoBufferConverter.convertFromI420(buffer, byteBuffer, FourCC.ARGB)

            SwingFXUtils.toFXImage(
                createBufferedImage(byteBuffer!!.array(), width, height),
                imageView!!.image as? WritableImage
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val update = { pixelBuffer!!.updateBuffer { pixBuffer: PixelBuffer<ByteBuffer?>? -> null } }
        try {
            if (Platform.isFxApplicationThread()) {
                update()
            } else {
                Platform.runLater(update)
            }
        } catch (e: java.lang.Exception) {
            throw RuntimeException(e)
        }
    }

    private fun initLayout(control: VideoView) {
        imageView = ResizableImageView()
        imageView?.isSmooth = false
        imageView?.isCache = false
        imageView?.isPreserveRatio = true
        border = Rectangle()
        border!!.styleClass.add("border")
        children.add(imageView)
        setResizable(control.getResize())
        registerChangeListener(
            control.resizeProperty()
        ) { o: ObservableValue<*>? ->
            setResizable(
                control.getResize()
            )
        }
    }

    private fun setResizable(resizable: Boolean) {
        val control = skinnable
        if (resizable) {
            if (Objects.nonNull(imageBoundsListener)) {
                imageView!!.layoutBoundsProperty().removeListener(imageBoundsListener)
            }
            imageView!!.clip = null
            children.remove(border)
            control!!.maxWidth = Region.USE_COMPUTED_SIZE
            controlBoundsListener =
                ChangeListener { _, _, newBounds ->
                    imageView!!.fitWidth = newBounds.width
                    imageView!!.fitHeight = newBounds.height
                }
            control.layoutBoundsProperty().addListener(controlBoundsListener)
        } else {
            if (Objects.nonNull(controlBoundsListener)) {
                control!!.layoutBoundsProperty().removeListener(controlBoundsListener)
            }
            control!!.maxWidth = Region.USE_PREF_SIZE
            children.add(border)
            imageBoundsListener =
                ChangeListener { _, _, newBounds ->
                    border!!.x = newBounds.minX
                    border!!.y = newBounds.minY
                    border!!.width = newBounds.width
                    border!!.height = newBounds.height
                    val clip =
                        Rectangle(newBounds.width, newBounds.height)
                    clip.arcWidth = border!!.arcWidth
                    clip.arcHeight = border!!.arcHeight
                    imageView!!.clip = clip
                }
            imageView!!.fitWidth = 0.0
            imageView!!.fitHeight = control.maxHeight
            imageView!!.layoutBoundsProperty().addListener(imageBoundsListener)
        }
    }

    private class ResizableImageView : ImageView() {
        override fun minWidth(height: Double): Double {
            return 100.0
        }

        override fun minHeight(width: Double): Double {
            return 100.0
        }
    }

    /**
     * Creates a new VideoViewSkin.
     *
     * @param control The control for which this Skin should attach to.
     */
    init {
        initLayout(control)
    }
}