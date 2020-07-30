package com.spqrta.camera2demo.screens.texture_camera

import android.Manifest
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.spqrta.camera2demo.*
import com.spqrta.camera2demo.base.BaseFragment
import com.spqrta.camera2demo.camera.BaseCameraWrapper
import com.spqrta.camera2demo.camera.PhotoCameraWrapper
import com.spqrta.camera2demo.camera.TextureViewWrapper
import com.spqrta.camera2demo.utility.Logger
import com.spqrta.camera2demo.utility.Meter
import com.spqrta.camera2demo.utility.utils.*
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_camera.*
import kotlinx.android.synthetic.main.layout_gallery.*

class TextureCameraFragment : BaseFragment<MainActivity>() {

    private val galleryAdapter = GalleryAdapter()

    private lateinit var cameraWrapper: PhotoCameraWrapper

    private lateinit var textureViewWrapper: TextureViewWrapper
    private val permissionsSubject = BehaviorSubject.create<Boolean>()
    private var cameraInitialized = false
    private val meter = Meter("activity", disabled = false)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraInitialized = false

        textureViewWrapper = TextureViewWrapper(textureView)

        rvGallery.layoutManager = GridLayoutManager(mainActivity(), 3)
        rvGallery.adapter = galleryAdapter
        galleryAdapter.onItemClickListener = {
            findNavController().navigate(
                TextureCameraFragmentDirections.actionTextureCameraFragmentToImageFragment(
                    it
                )
            )
        }

        bCloseGallery.setOnClickListener {
            hideGallery()
        }

        bClearGallery.setOnClickListener {
            FileUtils.clear(MyApplication.IMAGES_FOLDER)
            updateGallery()
            //todo recycler animation
        }

        bShot.setOnClickListener {
            onShotClicked()
        }

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

    private fun onShotClicked() {
        meter.log("shot")
        progressBar.show()
        cameraWrapper.takePhoto()
    }

    private fun initCamera() {
        val rotation = mainActivity().windowManager.defaultDisplay.rotation
        cameraWrapper = PhotoCameraWrapper(
            textureViewWrapper.subject,
            rotation,
            requireFrontFacing = false
        )
        cameraInitialized = true

        tvInfo.text = "size: ${cameraWrapper.size.toStringHw()}"

        cameraWrapper.focusStateObservable.subscribeManaged {
//            Logger.d(it)
            when (it) {
                is BaseCameraWrapper.Focusing -> {
                    ivFocus.clearColorFilter()
                    ivFocus.show()
                }
                is BaseCameraWrapper.Failed -> {
                    ivFocus.colorFilter = PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN)
                    ivFocus.show()
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

        adjustCameraView(textureView)
    }

    //todo refactor
    private fun onCameraResult(result: BaseCameraWrapper.BitmapCameraResult) {
        progressBar.hide()
        meter.log("result")
        startImageAnimation(result.bitmap)
        updateGallery()
    }

    private fun startImageAnimation(bitmap: Bitmap) {
        ivResult.show()
        ivResult.alpha = 1f
        ivResult.rotation = 0f
        ivResult.x = 0f
        ivResult.y = cameraView.y
        ivResult.scaleX = 1f
        ivResult.scaleY = 1f

        ivResult.setImageBitmap(bitmap)

        val targetX = bGallery.x + bGallery.measuredWidth / 2 - ivResult.measuredWidth / 2
        val targetY =
            (lBottom.y + bGallery.y) + bGallery.measuredHeight / 2 - ivResult.measuredHeight / 2
        val targetScaleX = bGallery.measuredWidth.toFloat() / ivResult.measuredWidth * 0.6f
        val targetScaleY = bGallery.measuredHeight.toFloat() / ivResult.measuredHeight * 0.6f
        val startY = ivResult.y
        val anim = ValueAnimator.ofFloat(1f, 0f)
        anim.interpolator = INTERPOLATOR
        anim.addUpdateListener { valueAnimator ->
            val animatorValue = valueAnimator.animatedValue as Float
            ivResult.scaleX = (animatorValue * (1 - targetScaleX)) + targetScaleX
            ivResult.scaleY = (animatorValue * (1 - targetScaleY)) + targetScaleY
            ivResult.x = (1 - animatorValue) * targetX
            ivResult.y = startY - ((1 - animatorValue) * (startY - targetY))
            ivResult.rotation = (1 - animatorValue) * -360
        }
        anim.duration = ANIM_DURATION
//        anim.addListener(object : AbstractSimpleAnimatorListener() {
//            override fun onAnimationEnd(animation: Animator?) {
//
//            }
//        })
        anim.start()

        ivResult.animate()
            .alpha(0f)
//            .setInterpolator(INTERPOLATOR)
            .setStartDelay(FADE_DELAY)
            .setDuration(FADE_DURATION)
            .withEndAction {
                ivResult.makeInvisible()
                ivResult.setImageBitmap(null)
                bitmap.recycle()
            }
            .start()
    }

    private fun adjustCameraView(cameraView: View) {
        val scale = PreviewAspectRatio.ratio480to640 / PreviewAspectRatio.getForSurface480to640(
            DeviceInfoUtils.getModelAndManufacturer(),
            frontFacing = false
        )
//        Logger.v(scale)
        cameraView.scaleY = scale
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
            textureViewWrapper.subject,
            permissionsSubject,
            BiFunction<BaseCameraWrapper.SurfaceState, Boolean,
                    Pair<BaseCameraWrapper.SurfaceState, Boolean>> { p1, p2 ->
                Pair(p1, p2)
            }
        ).subscribeManaged { pair ->
            val textureState = pair.first
            val permissionsAllowed = pair.second
            if (permissionsAllowed) {
                when (textureState) {
                    is BaseCameraWrapper.SurfaceAvailable -> {
                        if (!cameraInitialized) {
                            initCamera()

                            bGallery.setOnClickListener {
                                showGallery()
                            }

                            //todo other update triggers?
                            updateGallery()
                        }
                        cameraWrapper.open()
                    }
                }
            } else {
                //todo
            }
        }
    }

    private fun updateGallery() {
        //todo handle empty
        val images = MyApplication.IMAGES_FOLDER.listFiles()?.toList()
            ?.map { it.absolutePath }
        galleryAdapter.updateItems(images ?: listOf())
//        Logger.d(images)
    }

    private fun showGallery() {
        ivFocus.hide()
        ivResult.hide()
        lGallery.y = DeviceInfoUtils.getScreenSize().height.toFloat()
        lGallery.show()
        lGallery.animate().y(0f).start()
        //todo anim
        updateGallery()
    }

    private fun hideGallery() {
        lGallery.animate()
            .y(DeviceInfoUtils.getScreenSize().height.toFloat())
            .withEndAction {
                lGallery.hide()
            }
            .start()
    }

    override fun onBackPressed(): Boolean {
        return if (lGallery.isVisible) {
            hideGallery()
            true
        } else {
            false
        }
    }

    companion object {
        const val ANIM_DURATION = 700L
        const val FADE_DURATION = 350L
        const val FADE_DELAY = 600L
        val INTERPOLATOR = android.view.animation.AccelerateDecelerateInterpolator()
    }
}