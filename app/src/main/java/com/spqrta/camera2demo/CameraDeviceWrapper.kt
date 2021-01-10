/*
package com.spqrta.camera2demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import androidx.core.content.ContextCompat
import com.spqrta.camera2demo.camera.view_wrappers.TextureViewWrapper
import com.spqrta.camera2demo.utility.Analytics
import com.spqrta.camera2demo.utility.SubscriptionManager
import com.spqrta.camera2demo.utility.pure.BitmapUtils

import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import org.threeten.bp.LocalDateTime
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.min

//todo * limit video length
@SuppressLint("NewApi")
class CameraDeviceWrapper(
    private val textureViewWrapper: TextureViewWrapper,
    private val rotation: Int = 0,
    private val requiredAspectRatio: Float? = null,
    private val requireFrontFacing: Boolean = false,
    val captureVideo: Boolean = false
) : SubscriptionManager() {

    private val subject = BehaviorSubject.create<CameraResult>()
    val resultObservable: Observable<CameraResult> =
        subject.observeOn(AndroidSchedulers.mainThread())

    private var cameraManager: CameraManager

    private lateinit var cameraId: String
    private lateinit var characteristics: CameraCharacteristics
    private var cameraDevice: CameraDevice? = null

    private val previewSurface: Surface
        get() {
            val state = textureViewWrapper.subject.value
            if (state is TextureViewWrapper.TextureCreated) {
                return Surface(state.surfaceTexture)
            } else {
                throw IllegalStateException("Texture destroyed")
            }
        }

    private val videoSurface: Surface
        get() = mediaRecorder.surface

    private val orientation: Int
        get() = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

    val size: Size
        get() {
            val map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )!!
            return Collections.max(
                map.getOutputSizes(ImageFormat.JPEG).filter {
                    it.width < 4096 && it.height < 4096
                }.toList(),
                SizeComparatorByArea
            )
        }

    var isNotOpenOrOpening: Boolean = true

    private lateinit var captureSession: CameraCaptureSession
    private var sessionState: SessionState =
        Preview
        set(value) {
            field = value
//            Timber.v("$value")
        }

    private var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    private val filesDir = EMoneyApplication.context.externalCacheDir
    ///storage/emulated/0/Android/data/ge.emoney.app/files/pic.jpg
    private var videoFile: File = File(filesDir, "${LocalDateTime.now()}.mp4")

    private val imageReader: ImageReader
    private lateinit var mediaRecorder: MediaRecorder

    init {
        cameraManager =
            EMoneyApplication.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        chooseCamera()

        if(captureVideo) {
            mediaRecorder = MediaRecorder()
            setUpMediaRecorder(mediaRecorder)
        }

        imageReader = ImageReader.newInstance(
            size.width,
            size.height,
            ImageFormat.JPEG,
            2
        )

        imageReader.setOnImageAvailableListener({
            if (sessionState == SavingPicture) {
                mBackgroundHandler?.post {
                    val photoFile = File(filesDir, LocalDateTime.now().toString() + ".jpg")
                    photoFile.delete()
                    BitmapUtils.toFile(
                        photoFile.path,
                        cropResult(imageToBitmap(it.acquireLatestImage()))
                    )
                    sessionState = PictureSaved

                    if (captureVideo) {
                        subject.onNext(CameraResult(photoFile.path, videoFile.path))
                    } else {
                        subject.onNext(CameraResult(photoFile.path))
                    }
                    resetSession()
                }
            }
        }, mBackgroundHandler)
    }

    private fun chooseCamera() {
        try {
            for (_cameraId in cameraManager.cameraIdList) {
                val _characteristics = cameraManager.getCameraCharacteristics(_cameraId)
                characteristics = _characteristics

                val facing = _characteristics.get(CameraCharacteristics.LENS_FACING) ?: continue
                val frontFacing = facing == CameraCharacteristics.LENS_FACING_FRONT
                if (requireFrontFacing != frontFacing) continue

                cameraId = _cameraId
                return
            }
            subject.onError(NoCameraError())
        } catch (e: CameraAccessException) {
            subject.onError(e)
        }
    }

    fun open() {
        if (isNotOpenOrOpening) {
            try {
                isNotOpenOrOpening = false
                if (ContextCompat.checkSelfPermission(
                        EMoneyApplication.context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startBackgroundThread()

                    cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
//                            Timber.v("callback: camera opened")
                            cameraDevice = camera

                            createCaptureSession(
                                camera,
                                mutableListOf(previewSurface, imageReader.surface).apply {
                                    if (captureVideo) {
                                        add(videoSurface)
                                    }
                                }
                            ).subscribeManaged({ session ->
                                if (captureVideo) {
                                    startRecordingVideo()
                                }
                                startPreview(camera, session)

                            }, {
                                subject.onError(it)
                            })
                        }

                        override fun onClosed(camera: CameraDevice) {
                            isNotOpenOrOpening = true
//                            Timber.v("callback: camera closed")
                        }

                        override fun onDisconnected(camera: CameraDevice) {
                            isNotOpenOrOpening = true
//                            Timber.v("callback: camera disconnected")
                            cameraDevice = null
                            subject.onError(CameraDisconnected())
                        }

                        override fun onError(camera: CameraDevice, error: Int) {
                            isNotOpenOrOpening = true
                            cameraDevice = null
                            subject.onError(
                                CameraError(
                                    error
                                )
                            )
                        }
                    }, null);
                } else {
                    subject.onError(NoPermissionsError())
                }

            } catch (e: CameraAccessException) {
                subject.onError(e)
            }
        }
    }


    fun takePhoto() {
        if (cameraDevice != null) {
            try {
                val requestBuilder = createPrecaptureRequestBuilder(cameraDevice!!)
                val captureCallback = object : SimpleCaptureCallback() {
                    override fun process(result: CaptureResult) {
                        processCaptureResult(result)
                    }
                }
                sessionState =
                    Focusing
                captureSession.capture(requestBuilder.build(), captureCallback, mBackgroundHandler)
            } catch (e: CameraAccessException) {
                Analytics.logException(e)
                subject.onError(e)
            }
        }
    }

    fun close() {
        stopBackgroundThread()
        cameraDevice?.let {
            it.close()
            cameraDevice = null
        }
        disposeAll()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Preview
    ///////////////////////////////////////////////////////////////////////////

    private fun startPreview(cameraDevice: CameraDevice, session: CameraCaptureSession) {
        try {
            val requestBuilder = createPreviewRequestBuilder(cameraDevice, previewSurface, if(captureVideo) {
                videoSurface
            } else {
                null
            })
            session.setRepeatingRequest(requestBuilder.build(), null, null)
        } catch (e: CameraAccessException) {
            Analytics.logException(e)
            subject.onError(e)
        } catch (e: IllegalStateException) {
            Analytics.logException(e)
            subject.onError(e)
            */
/*if (e.message?.contains("was already closed") == false) {
                sessionSubject.onNext(Error)
            }*//*

        }
    }


    private fun createPreviewRequestBuilder(
        cameraDevice: CameraDevice,
        previewSurface: Surface,
        videoSurface: Surface?
    ): CaptureRequest.Builder {
        val requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        requestBuilder.addTarget(previewSurface)
        if(captureVideo) {
            requestBuilder.addTarget(videoSurface!!)
        }
        return requestBuilder
    }

    private fun createCaptureSession(
        cameraDevice: CameraDevice,
        surfaces: List<Surface>
    ): Single<CameraCaptureSession> {
        val subject = BehaviorSubject.create<CameraCaptureSession>()

        cameraDevice.createCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    subject.onNext(session)
                    subject.onComplete()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    subject.onError(SessionConfigureFailedException())
                }
            }, null
        )

        return Single.fromObservable(subject).doOnSuccess {
            captureSession = it
            sessionState = Initial
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Photo
    ///////////////////////////////////////////////////////////////////////////

    private fun processCaptureResult(result: CaptureResult) {
//        Timber.v("process ${sessionSubject.value!!::class.java.simpleName}")

        val autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE)
        val autoExposureState = result.get(CaptureResult.CONTROL_AE_STATE)
        when (sessionState) {
            is Preview, Initial -> {
            }
            is Focusing -> {
                when (autoFocusState) {
                    null -> {
                        //autoFocus not available
                        sessionState =
                            MakingShot
                        captureStillPicture()
                    }
                    CaptureResult.CONTROL_AF_STATE_INACTIVE,
                    CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                    CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED,
                    CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
                    CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> {
                        //autoFocus not activated properly or finished
                        when (autoExposureState) {
                            null, CaptureResult.CONTROL_AE_STATE_CONVERGED -> {
                                //autoExposure is ready or not available
                                sessionState =
                                    MakingShot
                                captureStillPicture()
                            }
                            else -> {
                                //autoExposure in progress
                                sessionState =
                                    AutoExposing
                                runPrecaptureSequence()
                            }
                        }
                    }
                    else -> {
                        val e = IllegalStateException(
                            "$sessionState $autoFocusState"
                        )
                        if (!BuildConfig.DEBUG) {
                            Analytics.logException(e)
                        } else {
                            throw e
                        }
                    }
                }
            }
            is AutoExposing -> {
                if (autoExposureState == null ||
                    autoExposureState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                    autoExposureState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
                ) {
                    //autoExposure is in progress, failed or not available
                    sessionState =
                        ReadyForShot
                }
            }
            is ReadyForShot -> {
                if (autoExposureState == null
                    || autoExposureState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE
                ) {
                    //autoExposure is ready, failed or not available
                    sessionState =
                        MakingShot
                    captureStillPicture()
                }
            }
        }
    }

    private fun runPrecaptureSequence() {
        try {
            val requestBuilder = createPrecaptureRequestBuilder(cameraDevice!!)
            val captureCallback = object : SimpleCaptureCallback() {
                override fun process(result: CaptureResult) {
                    processCaptureResult(result)
                }
            }
            captureSession.capture(requestBuilder.build(), captureCallback, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            Analytics.logException(e)
            sessionState =
                Error
            subject.onError(e)
        }

    }

    private fun captureStillPicture() {
        if(captureVideo) {
            try {
                stopRecordingVideo()
            } catch (e: RuntimeException) {
                Analytics.logException(e)
            } catch (e: IllegalStateException) {
                if (e.message != "stop called in an invalid state: 1") {
                    if (!BuildConfig.DEBUG) {
                        Analytics.logException(e)
                    } else {
                        throw e
                    }
                }
            }
        }
        try {
            val captureBuilder =
                createPhotoRequestBuilder(cameraDevice!!, previewSurface, imageReader.surface)

            captureSession.stopRepeating()
            captureSession.abortCaptures()
            captureSession.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {},
                null
            )
            sessionState =
                SavingPicture

        } catch (e: CameraAccessException) {
            Analytics.logException(e)
            sessionState =
                Error
            subject.onError(e)
        }

    }

    private fun createPrecaptureRequestBuilder(cameraDevice: CameraDevice): CaptureRequest.Builder {
        val requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        requestBuilder.addTarget(previewSurface)
        requestBuilder.set(
            CaptureRequest.CONTROL_AF_TRIGGER,
            CameraMetadata.CONTROL_AF_TRIGGER_START
        )
        return requestBuilder
    }

    private fun createPhotoRequestBuilder(
        cameraDevice: CameraDevice,
        previewSurface: Surface,
        photoSurface: Surface
    ): CaptureRequest.Builder {
        val requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        requestBuilder.addTarget(previewSurface)
        requestBuilder.addTarget(photoSurface)
        requestBuilder.set(
            CaptureRequest.JPEG_ORIENTATION,
            calculateOrientation(rotation, orientation)
        )
        requestBuilder.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
        requestBuilder.set(
            CaptureRequest.CONTROL_AF_TRIGGER,
            CameraMetadata.CONTROL_AF_TRIGGER_START
        )
        return requestBuilder
    }

    private fun calculateOrientation(screenOrientation: Int, deviceRotation: Int): Int {
        return (DEVICE_TO_SURFACE_ORIENTATION_MAP[screenOrientation] + deviceRotation + 270) % 360
    }

    private fun cropResult(bitmap: Bitmap): Bitmap {
        return if (requiredAspectRatio != null) {
            val requiredHeight = (bitmap.width / requiredAspectRatio).toInt()
//            check(requiredHeight <= bitmap.height) { "Required height bigger than original" }
            */
/*Bitmap.createBitmap(
                bitmap,
                0,
                (bitmap.height - requiredHeight) / 2,
                bitmap.width,
                requiredHeight
            )*//*

            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                min(requiredHeight, bitmap.height)
            )
        } else {
            bitmap
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        image.close()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
    }

    private fun resetSession() {
        if(captureVideo) {
            setUpMediaRecorder(mediaRecorder)
        }
        createCaptureSession(
            cameraDevice!!,
            mutableListOf(previewSurface, imageReader.surface).apply {
                if (captureVideo) {
                    add(videoSurface)
                }
            }
        ).subscribeManaged({ session ->
            if (captureVideo) {
                startRecordingVideo()
            }
            startPreview(cameraDevice!!, session)
        }, {
            subject.onError(it)
        })
    }

    ///////////////////////////////////////////////////////////////////////////
    // Video
    ///////////////////////////////////////////////////////////////////////////

    private fun startRecordingVideo() {
        mediaRecorder.start()
    }

    private fun stopRecordingVideo() {
        mediaRecorder.stop()
        mediaRecorder.reset()
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder(mediaRecorder: MediaRecorder) {
        videoFile = File(filesDir, "${LocalDateTime.now()}.mp4")
//        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setOutputFile(videoFile.absolutePath)
        mediaRecorder.setVideoEncodingBitRate(10000000)
        mediaRecorder.setVideoFrameRate(30)
        //todo /comm
        mediaRecorder.setVideoSize(640, 480)
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
//        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        //todo /bl
//        when (mSensorOrientation) {
//            SENSOR_ORIENTATION_DEFAULT_DEGREES -> mediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
//            SENSOR_ORIENTATION_INVERSE_DEGREES -> mediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
//        }
        mediaRecorder.prepare()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utility
    ///////////////////////////////////////////////////////////////////////////

    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        mBackgroundThread?.quitSafely()
    }

    fun reset() {
        sessionState = Initial
    }

    ///////////////////////////////////////////////////////////////////////////
    // Classes
    ///////////////////////////////////////////////////////////////////////////

    object SizeComparatorByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
        }
    }

    open class CameraResult(val photoPath: String, val videoPath: String? = null)

    class CameraDisconnected : Exception()
    class CameraError(private val errorCode: Int) : Exception() {
        override val message: String?
            get() = "Camera Error: code $errorCode"
    }

    class NoPermissionsError : Exception()
    class NoCameraError : Exception()

    open class SessionState {
        override fun toString(): String {
            return "session: ${this::class.java.simpleName}"
        }
    }

    object Initial : SessionState()
    object Preview : SessionState()
    object MakingShot : SessionState()
    object AutoExposing : SessionState()
    object Focusing : SessionState()
    object ReadyForShot : SessionState()
    object SavingPicture : SessionState()
    object PictureSaved : SessionState()
    object Error : SessionState()

    abstract class SimpleCaptureCallback : CameraCaptureSession.CaptureCallback() {

        abstract fun process(result: CaptureResult)

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            process(partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            process(result)
        }
    }

    class SessionConfigureFailedException : Exception()

    companion object {
        val DEVICE_TO_SURFACE_ORIENTATION_MAP = SparseIntArray(4)

        init {
            DEVICE_TO_SURFACE_ORIENTATION_MAP.append(Surface.ROTATION_0, 90)
            DEVICE_TO_SURFACE_ORIENTATION_MAP.append(Surface.ROTATION_90, 0)
            DEVICE_TO_SURFACE_ORIENTATION_MAP.append(Surface.ROTATION_180, 270)
            DEVICE_TO_SURFACE_ORIENTATION_MAP.append(Surface.ROTATION_270, 180)
        }
    }

}*/
