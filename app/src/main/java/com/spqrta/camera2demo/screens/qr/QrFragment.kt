package com.spqrta.camera2demo.screens.qr

import android.Manifest
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import com.spqrta.camera2demo.MainActivity
import com.spqrta.camera2demo.R
import com.spqrta.camera2demo.base.display.BaseFragment
import com.spqrta.camera2demo.camera.BaseCameraWrapper
import com.spqrta.camera2demo.camera.PhotoCameraWrapper
import com.spqrta.camera2demo.camera.QrCameraWrapper
import com.spqrta.camera2demo.camera.view_wrappers.SurfaceViewWrapper
import com.spqrta.camera2demo.utility.pure.*
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.android.synthetic.main.fragment_camera.cameraView
import kotlinx.android.synthetic.main.fragment_camera.ivFocus
import kotlinx.android.synthetic.main.fragment_camera.view.*
import kotlinx.android.synthetic.main.fragment_qr.*

class QrFragment : BaseFragment<MainActivity>() {

    private lateinit var cameraWrapper: QrCameraWrapper

    private lateinit var surfaceViewWrapper: SurfaceViewWrapper
    private val permissionsSubject = BehaviorSubject.create<Boolean>()
    private var cameraInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_qr, container, false)
        v.cameraView.removeAllViews()
        v.cameraView.addView(SurfaceView(mainActivity()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER
            )
//            setBackgroundColor(Color.RED)
        })
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraInitialized = false

        surfaceViewWrapper = SurfaceViewWrapper(cameraView.getChildAt(0) as SurfaceView)

        initObservables()
        triggerAskForPermissions()
    }

    override fun onPause() {
        super.onPause()
        if (cameraInitialized) {
            cameraWrapper.close()
        }
    }

    override fun onResume() {
        super.onResume()
        if (cameraInitialized) {
            cameraWrapper.open()
        }
    }

    private fun initCamera() {
        val rotation = mainActivity().windowManager.defaultDisplay.rotation
        cameraWrapper = QrCameraWrapper(
            {
                surfaceViewWrapper.surface
            },
            rotation,
        )

        var previewSize = cameraWrapper.getAvailablePreviewSizesRegardsOrientation().firstOrNull()
        if (previewSize == null) {
            previewSize = cameraWrapper.getSizeRegardsOrientation()
        }

        val lp = surfaceViewWrapper.surfaceView.layoutParams
        lp.height = (
                surfaceViewWrapper.surfaceView.measuredWidth /
                        (previewSize.width / previewSize.height.toFloat())
                ).toInt()
        surfaceViewWrapper.surfaceView.layoutParams = lp

        surfaceViewWrapper.setSurfaceSize(previewSize)

        cameraInitialized = true

        cameraWrapper.focusStateObservable.subscribeManaged {
            when (it) {
                is BaseCameraWrapper.Focusing -> {
                    ivFocus.clearColorFilter()
                    ivFocus.show()
                }
                is BaseCameraWrapper.Failed -> {
//                    ivFocus.colorFilter = PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
//                    ivFocus.show()
                    ivFocus.hide()
                }
                else -> {
                    ivFocus.hide()
                }
            }
        }

        cameraWrapper.resultObservable.subscribeManaged({ result ->
            onCameraResult(result)
        }, {
            throw it
        })
    }

    //    private fun onCameraResult(result: BaseCameraWrapper.BitmapCameraResult) {
    private fun onCameraResult(result: QrCameraWrapper.StringCameraResult) {
//    ivDebResult.setImageBitmap(result.bitmap)
        result.result?.let {
            tvQrResult.text = it
        }
    }

    private fun triggerAskForPermissions() {
        RxPermissions(this).requestEach(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ).toList().subscribeManaged { list ->
            if (list.all { it.granted }) {
                permissionsSubject.onNext(true)
            } else {
                //todo handle not granted
                //todo hide gallery
                permissionsSubject.onNext(false)
            }
        }
    }

    private fun initObservables() {
        Observable.combineLatest(
            surfaceViewWrapper.subject,
            permissionsSubject,
            BiFunction<SurfaceViewWrapper.SurfaceViewState, Boolean,
                    Pair<SurfaceViewWrapper.SurfaceViewState, Boolean>> { p1, p2 ->
                Pair(p1, p2)
            }
        ).subscribeManaged { pair: Pair<SurfaceViewWrapper.SurfaceViewState, Boolean> ->
            val surfaceViewState = pair.first
            val permissionsAllowed = pair.second
            if (permissionsAllowed) {
                when (surfaceViewState) {
                    is SurfaceViewWrapper.SurfaceAvailable -> {
                        if (!cameraInitialized) {
                            initCamera()
                        }
                        cameraWrapper.open()
                    }
                    else -> {
                        //todo
                        cameraWrapper.close()
                        cameraInitialized = false
                    }
                }
            } else {
                //todo
            }
        }
    }

}