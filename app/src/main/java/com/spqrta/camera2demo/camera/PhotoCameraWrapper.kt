package com.spqrta.camera2demo.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.hardware.camera2.CameraAccessException
import android.media.ImageReader
import android.util.Size
import android.view.Surface
import com.spqrta.camera2demo.MyApplication
import com.spqrta.camera2demo.screens.surface_camera.SurfaceCameraFragment
import com.spqrta.camera2demo.utility.Logger
import com.spqrta.camera2demo.utility.Meter
import com.spqrta.camera2demo.utility.utils.BitmapUtils
import io.reactivex.subjects.BehaviorSubject
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter

@SuppressLint("NewApi")
class PhotoCameraWrapper(
    previewSurfaceProvider: ()-> Surface,
    rotation: Int = 0,
    requiredAspectRatio: Float? = null,
    requireFrontFacing: Boolean = false
) : BaseCameraWrapper<BaseCameraWrapper.BitmapCameraResult>(
    previewSurfaceProvider = previewSurfaceProvider,
    rotation = rotation,
    requiredImageAspectRatioHw = requiredAspectRatio,
    requireFrontFacing = requireFrontFacing
) {

    private val meter = Meter("camera", disabled = true)

    override val subject = BehaviorSubject.create<BitmapCameraResult>()

    override fun provideImageSize(): Size {
        //todo
//        val exactSize = Size(1536, 2048)
//        return chooseCameraSize(exact = exactSize)

//        val exactSize = Size(1536, 2048)
//        return chooseCameraSize(exact = exactSize)
//        return chooseCameraSize(topLimit = 4000)
//        return chooseCameraSize(aspectRatioHw = 720f/1280)
        return chooseCameraSize()
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
        startPreview(listOf(previewSurfaceProvider?.invoke()!!))
        meter.log("finish")
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val filename = LocalDateTime.now().format(
            DateTimeFormatter.ISO_DATE_TIME
        ) + ".jpg"
        val filePath = MyApplication.IMAGES_FOLDER.absolutePath + "/" + filename
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