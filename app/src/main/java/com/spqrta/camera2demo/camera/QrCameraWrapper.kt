package com.spqrta.camera2demo.camera

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.media.ImageReader
import android.util.Size
import android.view.Surface
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.spqrta.camera2demo.MyApplication
import com.spqrta.camera2demo.screens.surface_camera.SurfaceCameraFragment
import com.spqrta.camera2demo.utility.Logg
import com.spqrta.camera2demo.utility.Meter
import com.spqrta.camera2demo.utility.pure.BitmapUtils
import io.reactivex.subjects.BehaviorSubject
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.IOException
import java.lang.Boolean
import java.lang.Exception
import java.net.URLDecoder
import java.util.*

@SuppressLint("NewApi")
class QrCameraWrapper(
    previewSurfaceProvider: () -> Surface,
    rotation: Int = 0,
) : BaseCameraWrapper<QrCameraWrapper.StringCameraResult>(
    previewSurfaceProvider = previewSurfaceProvider,
    rotation = rotation,
    requireFrontFacing = false
) {

    override val subject = BehaviorSubject.create<StringCameraResult>()

    override fun provideImageSize(): Size {
        return chooseCameraSize()
    }

    override fun createImageReader(): ImageReader {
        return ImageReader.newInstance(
            size.width / 16,
            size.height / 16,
            ImageFormat.JPEG,
            2
        )
    }

    override open fun onCaptureSessionCreated() {
        if (hasPreview) {
            startPreview(listOf(previewSurfaceProvider?.invoke()!!, imageReader.surface))
        }
    }

    override fun handleImageAndClose(imageReader: ImageReader) {
        imageReader.acquireLatestImage()?.let {
            val bitmap = BitmapUtils.rotate(
                cropResult(
                    imageToBitmapAndClose(it)
                ),
                calculateOrientation(rotation, characteristics.sensorOrientation)
            )
            val text = decodeQRCode(bitmap)
            subject.onNext(StringCameraResult(text))
        }
//        sessionState = PictureSaved
//
//
////
//        startPreview(listOf(previewSurfaceProvider?.invoke()!!))
    }

    private fun decodeQRCode(bitmap: Bitmap): String? {
        try {
            val multiFormatReader = MultiFormatReader()
            val hints: MutableMap<DecodeHintType, Any?> = Hashtable()
            hints[DecodeHintType.PURE_BARCODE] = Boolean.TRUE
            multiFormatReader.setHints(hints)

            val width: Int = bitmap.width
            val height: Int = bitmap.height

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val bitmapRes = BinaryBitmap(HybridBinarizer(source))
            val result = MultiFormatReader().decode(bitmapRes)
            return URLDecoder.decode(result.text, Charsets.UTF_8.name())
        } catch (e: NotFoundException) {
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    open class StringCameraResult(val result: String?)

}