package com.spqrta.camera2demo.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import androidx.core.content.ContextCompat
import com.spqrta.camera2demo.utility.CustomApplication
import com.spqrta.camera2demo.utility.Logger
import com.spqrta.camera2demo.utility.Meter
import com.spqrta.camera2demo.utility.SubscriptionManager
import com.spqrta.camera2demo.utility.utils.toStringHw
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.min

//todo check if subject store past values? possible leak
@SuppressLint("NewApi")
abstract class BaseCameraWrapper<T>(
    protected val rotation: Int = 0,
    protected val surfaceStateSubject: BehaviorSubject<SurfaceState>? = null,
    protected val requiredPreviewAspectRatioHw: Float? = null,
    protected val requiredImageAspectRatioHw: Float? = null,
    protected val requireFrontFacing: Boolean = false
//    private val analytics: Analytics? = null
) : SubscriptionManager() {

    private val meter = Meter("base")

    protected abstract val subject: BehaviorSubject<T>
    open val resultObservable: Observable<T> by lazy {
        subject.observeOn(AndroidSchedulers.mainThread())
    }

    private val focusSubject = BehaviorSubject.create<FocusState>()
    open val focusStateObservable: Observable<FocusState> =
        focusSubject
            .observeOn(AndroidSchedulers.mainThread())
            .distinctUntilChanged()

    private var cameraManager: CameraManager

    private lateinit var cameraId: String
    private lateinit var characteristics: CameraCharacteristics
    protected var cameraDevice: CameraDevice? = null

    protected val previewSurface: Surface?
        get() {
            if (surfaceStateSubject != null) {
                val state = surfaceStateSubject.value
                if (state is SurfaceAvailable) {
                    return state.surface
                } else {
                    throw IllegalStateException("Surface destroyed")
                }
            } else return null
        }

    val hasPreview = previewSurface != null

    protected val orientation: Int
        get() = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

    open val size: Size by lazy {
        provideImageSize()
    }

    protected open fun provideImageSize(): Size {
        return chooseCameraSize(requiredImageAspectRatioHw)
    }

    var isNotOpenOrOpening: Boolean = true

    protected lateinit var captureSession: CameraCaptureSession
    protected var sessionInitialized: Boolean = false
    var sessionState: SessionState = Preview
        set(value) {
            field = value
            Logger.v(value)
        }

    protected val captureCallback = object : SimpleCaptureCallback() {
        override fun process(result: CaptureResult, completed: Boolean) {
            processCaptureResult(result, completed)
        }
    }

    protected var mBackgroundHandler: Handler? = null
    private var mBackgroundThread: HandlerThread? = null

    protected val imageReader: ImageReader

    init {
        cameraManager =
            CustomApplication.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        chooseCamera()

        imageReader = ImageReader.newInstance(
            size.width,
            size.height,
            ImageFormat.JPEG,
            2
        )

        imageReader.setOnImageAvailableListener({
            mBackgroundHandler?.post {
                onNewFrame(it)
            }
        }, mBackgroundHandler)

        surfaceStateSubject?.subscribeManaged {
            if(it is SurfaceDestroyed) {
                Logger.e("Surface Destroyed")
                close()
            }
        }
    }

    protected open fun onNewFrame(imageReader: ImageReader) {
        try {
            Logger.v("onNewFrame")
            handleImageAndClose(imageReader)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("maxImages") == true) {

            } else if (e.message?.contains("sending message to a Handler on a dead thread") == true) {
//todo search everywhere
//                CustomApplication.analytics().logException(e)
            } else {
                throw e
            }
        }
    }

    protected open fun handleImageAndClose(imageReader: ImageReader) {}

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

    @SuppressLint("MissingPermission")
    fun open() {
        if (isNotOpenOrOpening) {
            try {
                isNotOpenOrOpening = false
                if (ContextCompat.checkSelfPermission(
                        CustomApplication.context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startBackgroundThread()

                    cameraManager.openCamera(
                        cameraId,
                        object : CameraDevice.StateCallback() {
                            override fun onOpened(camera: CameraDevice) {
                                try {
                                    Logger.v("callback: camera opened")
                                    cameraDevice = camera
                                    onCameraOpened(camera)
                                } catch (e: IllegalStateException) {
                                    if (e.message != "Texture destroyed") {
                                        throw e
                                    }
                                }
                            }

                            override fun onClosed(camera: CameraDevice) {
                                isNotOpenOrOpening = true
                                Logger.v("callback: camera closed")
                            }

                            override fun onDisconnected(camera: CameraDevice) {
                                isNotOpenOrOpening = true
                                Logger.v("callback: camera disconnected")
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
                        },
                        null
                    );
                } else {
                    subject.onError(NoPermissionsError())
                }

            } catch (e: CameraAccessException) {
                subject.onError(e)
            }
        }
    }

    protected open fun onCameraOpened(camera: CameraDevice) {
        createCaptureSession(
            camera,
            getAvailableSurfaces()
        ).subscribeManaged({ session ->
            onCaptureSessionCreated()
        }, {
            subject.onError(it)
        })
    }

    protected open fun onCaptureSessionCreated() {
        if (hasPreview) {
            startPreview(listOf(previewSurface!!))
        }
    }

    fun close() {
        stopBackgroundThread()
        cameraDevice?.let {
            cameraDevice = null
            it.close()
        }
        disposeAll()
    }

    ///////////////////////////////////////////////////////////////////////////
    // Preview
    ///////////////////////////////////////////////////////////////////////////

    protected fun startPreview(surfaces: List<Surface>) {
        try {
            val requestBuilder = createPreviewRequestBuilder(
                cameraDevice!!, surfaces
            )
            sessionState = Preview
            captureSession.setRepeatingRequest(requestBuilder.build(), captureCallback, null)
        } catch (e: CameraAccessException) {
//            CustomApplication.analytics().logException(e)
            subject.onError(e)
        } catch (e: IllegalStateException) {
//            CustomApplication.analytics().logException(e)
            subject.onError(e)
        }
    }


    private fun createPreviewRequestBuilder(
        cameraDevice: CameraDevice,
        surfaces: List<Surface>
    ): CaptureRequest.Builder {
        val requestBuilder =
            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        surfaces.forEach {
            requestBuilder.addTarget(it)
        }
        requestBuilder.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        )
//        requestBuilder.set(
//            CaptureRequest.CONTROL_AF_TRIGGER,
//            CameraMetadata.CONTROL_AF_TRIGGER_START
//        )
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
            sessionInitialized = true
            sessionState = Initial
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Photo
    ///////////////////////////////////////////////////////////////////////////

    private fun processCaptureResult(
        result: CaptureResult,
        completed: Boolean
    ) {
        val autoFocusState = result.get(CaptureResult.CONTROL_AF_STATE)
        val autoExposureState = result.get(CaptureResult.CONTROL_AE_STATE)

        when (autoFocusState) {
            CaptureResult.CONTROL_AF_STATE_INACTIVE -> {}
            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> {
                focusSubject.onNext(Focusing)
            }
            null,
            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> {
                focusSubject.onNext(Focused)
            }
            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED,
            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED-> {
                focusSubject.onNext(Failed)
            }
        }

        if (sessionState != Preview) {
            Logger.v("process ${sessionState::class.java.simpleName}, AE: ${autoExposureState}, AF: ${autoFocusState}, completed: $completed")
        }

        when (sessionState) {
            is Preview, Initial, MakingShot -> {
//                Logger.v("preview AF: ${autoFocusState}")
            }
            is Precapture -> {
                if (completed) {
                    onPrecaptureFinished()
                } else when (autoFocusState) {
                    null,
                    CaptureResult.CONTROL_AF_STATE_INACTIVE -> {
//                        Logger.v("autoFocus not available")
                        processAutoExposure(autoExposureState)
                    }
                    CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> {
//                        Logger.v("autoFocus in progress")
                    }
                    CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                    CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED,
                    CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> {
//                        Logger.v("autoFocus finished")
                        processAutoExposure(autoExposureState)
                    }
                    else -> {
                        val e = IllegalStateException("$sessionState $autoFocusState")
                        //todo
                        e.printStackTrace()
//                        CustomApplication.analytics().logException(e)
                    }
                }
            }
        }
    }

    private fun processAutoExposure(autoExposureState: Int?) {
        when (autoExposureState) {
            null,
            CaptureResult.CONTROL_AE_STATE_INACTIVE,
            CaptureResult.CONTROL_AE_STATE_PRECAPTURE,
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED,
            CaptureResult.CONTROL_AE_STATE_CONVERGED,
            CaptureResult.CONTROL_AE_STATE_LOCKED -> {
                onPrecaptureFinished()
            }
            CaptureResult.CONTROL_AE_STATE_SEARCHING -> {
//                Logger.v("autoExposure is in progress: ${autoExposureState}")
            }
            else -> {
                val e = IllegalStateException(
                    "$sessionState $autoExposureState"
                )
                if (CustomApplication.appConfig.debugMode) {
                    throw e
                }
//                CustomApplication.analytics().logException(e)
            }
        }
    }

    private fun onPrecaptureFinished() {
        Logger.v("onPrecaptureFinished")
        sessionState = MakingShot
        captureStillPicture()
    }

    protected fun runPrecaptureSequence() {
        try {
            val requestBuilder = createAutoFocusSequenceRequestBuilder(cameraDevice!!)
            sessionState = Precapture
            captureSession.capture(
                requestBuilder.build(),
                captureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
//            CustomApplication.analytics().logException(e)
            sessionState =
                Error
            subject.onError(e)
        }

    }

    protected fun captureStillPicture() {
        try {
            val captureBuilder = createPhotoRequestBuilder(
                cameraDevice!!,
                imageReader.surface,
                previewSurface = previewSurface
            )

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
//            CustomApplication.analytics().logException(e)
            sessionState =
                Error
            subject.onError(e)
        }

    }

    //todo changes to reusables
    protected fun createAutoFocusSequenceRequestBuilder(cameraDevice: CameraDevice): CaptureRequest.Builder {
        val requestBuilder =
            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        previewSurface?.let { requestBuilder.addTarget(it) }
        requestBuilder.set(
            CaptureRequest.CONTROL_AF_TRIGGER,
            CameraMetadata.CONTROL_AF_TRIGGER_START
        )
        return requestBuilder
    }

    protected fun createPhotoRequestBuilder(
        cameraDevice: CameraDevice,
        photoSurface: Surface,
        previewSurface: Surface? = null
    ): CaptureRequest.Builder {
        val requestBuilder =
            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        previewSurface?.let { requestBuilder.addTarget(it) }
        requestBuilder.addTarget(photoSurface)
        requestBuilder.set(
            CaptureRequest.JPEG_ORIENTATION,
            0
//            calculateOrientation(rotation, orientation)
        )
//        Logger.d("orientation ${rotation} ${orientation} ${calculateOrientation(rotation, orientation)}")
        requestBuilder.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_AUTO
        )
        return requestBuilder
    }

    protected fun cropResult(bitmap: Bitmap): Bitmap {
        return if (requiredImageAspectRatioHw != null) {
            val requiredHeight = (bitmap.width / requiredImageAspectRatioHw).toInt()
//            check(requiredHeight <= bitmap.height) { "Required height bigger than original" }
            /*Bitmap.createBitmap(
                bitmap,
                0,
                (bitmap.height - requiredHeight) / 2,
                bitmap.width,
                requiredHeight
            )*/
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

    protected open fun getAvailableSurfaces(): List<Surface> {
        val surfaceList = mutableListOf(imageReader.surface)
        previewSurface?.let { surfaceList.add(it) }
        return surfaceList
    }

    fun chooseCameraSize(
        aspectRationHw: Float? = null,
        bottomLimit: Int = 0,
        topLimit: Int? = null,
        exactHeight: Int? = null,
        smallest: Boolean = false
    ): Size {
        val map = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        )!!

        Logger.v(map.getOutputSizes(ImageFormat.JPEG).joinToString(" ") {
            it.toStringHw()
        })

        val sizes = map.getOutputSizes(ImageFormat.JPEG).toList()

        var selectedSizes = sizes

        //choose exact size
        exactHeight?.let { h ->
            selectedSizes = sizes.filter { it.height == h }
            if (selectedSizes.isNotEmpty()) {
                return selectedSizes[0]
            }
        }


        val filteredSizes = sizes
            .filter {
                if (topLimit != null) {
                    it.width <= topLimit && it.height <= topLimit
                } else {
                    true
                }
            }
            .filter {
                it.width >= bottomLimit && it.height >= bottomLimit
            }

        //choose by aspect ratio
        selectedSizes = filteredSizes
            .filter {
                if (aspectRationHw != null) {
                    it.height / it.width.toFloat() == aspectRationHw
                } else true
            }
            .toList()

        if (selectedSizes.isEmpty()) {
            Logger.e("required aspect ratio not found")
//                CustomApplication.analytics()
//                    .logException(Exception("required aspect ratio not found"))
            selectedSizes = filteredSizes
        }

        if (selectedSizes.isEmpty()) {
            Logger.e("size range not found")
//                CustomApplication.analytics().logException(Exception("size range not found"))
            selectedSizes = sizes
        }

        val selectedSize = if (smallest) {
            Collections.min(
                selectedSizes,
                SizeComparatorByArea
            )
        } else {
            Collections.max(
                selectedSizes,
                SizeComparatorByArea
            )
        }
        Logger.v("size ${selectedSize.height} ${selectedSize.width} ${selectedSize.height / selectedSize.width.toFloat()}")
        return selectedSize
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

    open class StubCameraResult
    open class BitmapCameraResult(val bitmap: Bitmap)
    open class BytesCameraResult(val bytes: ByteArray)
    open class FileCameraResult(
        val photoPath: String,
        val videoPath: String? = null
    )

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

    open class SurfaceState
    class SurfaceAvailable(val surface: Surface) : SurfaceState()
    object SurfaceDestroyed : SurfaceState()

    object Initial : SessionState()
    object Preview : SessionState()
    object MakingShot : SessionState()
    object Precapture : SessionState()
    object SavingPicture : SessionState()
    object PictureSaved : SessionState()
    object Error : SessionState()

    open class FocusState
    object Focusing : FocusState()
    object Focused : FocusState()
    object Failed : FocusState()

    abstract class SimpleCaptureCallback : CameraCaptureSession.CaptureCallback() {

        abstract fun process(result: CaptureResult, completed: Boolean)

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            process(partialResult, false)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            process(result, completed = true)
        }
    }

    class SessionConfigureFailedException : Exception()


    companion object {

        fun imageToBytesAndClose(image: Image): ByteArray {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            image.close()
            return bytes
        }

        fun imageToBitmapAndClose(image: Image): Bitmap {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            image.close()
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
        }

        fun bytesToBitmap(buffer: ByteBuffer): Bitmap {
            val bytes = ByteArray(buffer.capacity())
            buffer.get(bytes)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
        }

        fun calculateOrientation(screenOrientation: Int, deviceRotation: Int): Int {
            return (DEVICE_TO_SURFACE_ORIENTATION_MAP[screenOrientation] + deviceRotation + 270) % 360
        }

        val DEVICE_TO_SURFACE_ORIENTATION_MAP = SparseIntArray(4).apply {
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90, 0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }

    }


}