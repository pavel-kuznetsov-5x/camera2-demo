package com.spqrta.camera2demo.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.hardware.camera2.CameraAccessException
import android.media.ImageReader
import android.util.Size
import com.spqrta.camera2demo.utility.CustomApplication
import com.spqrta.camera2demo.utility.Logger
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

    override val subject = BehaviorSubject.create<BitmapCameraResult>()

    override fun provideImageSize(): Size {
        //todo
        return chooseCameraSize(requiredImageAspectRatioHw, topLimit = 512)
    }

    override fun handleImageAndClose(imageReader: ImageReader) {
        sessionState = PictureSaved
        val bitmap = BitmapUtils.rotate(
            cropResult(
                imageToBitmapAndClose(imageReader.acquireLatestImage())
            ),
            calculateOrientation(rotation, orientation)
        )
        val thumbnail = BitmapUtils.scale(bitmap, width = 24)
        subject.onNext(BitmapCameraResult(thumbnail))
        saveToGallery(bitmap)
        startPreview(listOf(previewSurface!!))
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val filename = LocalDateTime.now().format(
            DateTimeFormatter.ISO_DATE_TIME
        ) + ".jpg"
        val filePath = CustomApplication.context.externalCacheDir?.absolutePath + "/" + filename
        Logger.d(filePath)
        BitmapUtils.toFile(filePath, bitmap, quality = 95)
    }


    //todo W/System: A resource failed to call release.
    fun takePhoto() {
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