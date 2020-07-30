package com.spqrta.camera2demo.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.hardware.camera2.CameraAccessException
import android.media.ImageReader
import android.util.Size
import com.spqrta.camera2demo.MainActivity
import com.spqrta.camera2demo.utility.CustomApplication
import com.spqrta.camera2demo.utility.Logger
import com.spqrta.camera2demo.utility.Meter
import com.spqrta.camera2demo.utility.utils.BitmapUtils
import io.reactivex.subjects.BehaviorSubject
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter

@SuppressLint("NewApi")
class PhotoCameraWrapper(
    textureViewWrapper: TextureViewWrapper,
    rotation: Int = 0,
    requiredAspectRatio: Float? = null,
    requireFrontFacing: Boolean = false
) : BaseCameraWrapper<BaseCameraWrapper.BitmapCameraResult>(
    textureViewWrapper = textureViewWrapper,
    rotation = rotation,
    requiredImageAspectRatioHw = requiredAspectRatio,
    requireFrontFacing = requireFrontFacing
) {

    private val meter = Meter("camera", disabled = true)

    override val subject = BehaviorSubject.create<BitmapCameraResult>()

    override fun provideImageSize(): Size {
        //todo
        return chooseCameraSize(requiredImageAspectRatioHw, topLimit = 999999)
    }

    override fun handleImageAndClose(imageReader: ImageReader) {
        meter.log("start handle")
        sessionState = PictureSaved
        val bitmap = BitmapUtils.rotate(
            cropResult(
                imageToBitmapAndClose(imageReader.acquireLatestImage())
            ),
            calculateOrientation(rotation, orientation)
        )
        meter.log("prepare")
        val thumbnail = BitmapUtils.scale(bitmap, width = 128)
        meter.log("thumbnail")
        subject.onNext(BitmapCameraResult(thumbnail))
        saveToGallery(bitmap)
        meter.log("save")
        startPreview(listOf(previewSurface!!))
        meter.log("finish")
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val filename = LocalDateTime.now().format(
            DateTimeFormatter.ISO_DATE_TIME
        ) + ".jpg"
        val filePath = MainActivity.IMAGES_FOLDER.absolutePath + "/" + filename
//        Logger.d(filePath)
        BitmapUtils.toFile(filePath, bitmap, quality = 95)
    }


    //todo W/System: A resource failed to call release.
    fun takePhoto() {
        meter.log("takePhoto")
        if (cameraDevice != null) {
            try {
                runPrecaptureSequence()
            } catch (e: CameraAccessException) {
//                CustomApplication.analytics().logException(e)
                subject.onError(e)
            }
        }
    }

}