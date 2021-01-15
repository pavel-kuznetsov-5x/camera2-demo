package com.spqrta.camera2demo.camera

import android.annotation.SuppressLint
import android.util.Size
import android.view.Surface
import com.spqrta.camera2demo.MyApplication
import com.spqrta.camera2demo.camera.media.VideoRecorder
import com.spqrta.camera2demo.utility.Toaster
import io.reactivex.subjects.BehaviorSubject
import java.lang.RuntimeException

@Suppress("JoinDeclarationAndAssignment")
@SuppressLint("NewApi")
class VideoCameraWrapper(
    previewSurfaceProvider: () -> Surface,
    rotation: Int = 0,
    requiredAspectRatio: Float? = null,
    requireFrontFacing: Boolean = false
) : BaseCameraWrapper<BaseCameraWrapper.BitmapCameraResult>(
    previewSurfaceProvider = previewSurfaceProvider,
    rotation = rotation,
    requiredImageAspectRatioHw = requiredAspectRatio,
    requireFrontFacing = requireFrontFacing
) {

    override val subject = BehaviorSubject.create<BitmapCameraResult>()

    private var videoRecorder: VideoRecorder

    private val filesDir = MyApplication.VIDEOS_FOLDER

    val isRecording: Boolean
        get() = videoRecorder.isRecording

    init {
        videoRecorder = VideoRecorder(filesDir, calculateOrientation(
            rotation,
            characteristics.sensorOrientation
        ))
        videoRecorder.initMediaRecorder()
    }

    override fun provideImageSize(): Size {
        return chooseCameraSize()
    }

    override fun getAvailableSurfaces(): List<Surface> {
        return mutableListOf<Surface>().apply {
            addAll(super.getAvailableSurfaces())
            add(videoRecorder.surface)
        }
    }

    override fun onCaptureSessionCreated() {
        startPreview(mutableListOf<Surface>().apply {
            if (hasPreview) {
                add(previewSurfaceProvider?.invoke()!!)
            }
            add(videoRecorder.surface)
        })
    }

    fun startRecording() {
        if (cameraDevice != null) {
            videoRecorder.start()
        }
    }

    fun stopRecording() {
        videoRecorder.stop()

        createCaptureSession(
            cameraDevice!!,
            getAvailableSurfaces()
        ).subscribeManaged({ session ->
            onCaptureSessionCreated()
        }, {
            subject.onError(it)
        })
    }



}