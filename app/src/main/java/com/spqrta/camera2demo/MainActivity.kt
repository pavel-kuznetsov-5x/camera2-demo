package com.spqrta.camera2demo

import android.Manifest
import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.os.Bundle
import android.view.TextureView
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.spqrta.camera2demo.base.BaseActivity
import com.spqrta.camera2demo.camera.BaseCameraWrapper
import com.spqrta.camera2demo.camera.PhotoCameraWrapper
import com.spqrta.camera2demo.camera.TextureViewWrapper
import com.spqrta.camera2demo.utility.CustomApplication
import com.spqrta.camera2demo.utility.Logger
import com.spqrta.camera2demo.utility.utils.*
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.activity_main.*
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.lang.Math.abs


class MainActivity : BaseActivity() {

    override val layoutRes = R.layout.activity_main

    private val galleryAdapter = GalleryAdapter()

    private lateinit var cameraWrapper: PhotoCameraWrapper
    private lateinit var textureViewWrapper: TextureViewWrapper
    private val permissionsSubject = BehaviorSubject.create<Boolean>()
    private var cameraInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textureViewWrapper = TextureViewWrapper(textureView)

        rvGallery.layoutManager = GridLayoutManager(this, 3)
        rvGallery.adapter = galleryAdapter

        bShot.setOnClickListener {
            cameraWrapper.takePhoto()
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

    private fun initCamera() {
        val rotation = windowManager.defaultDisplay.rotation
        cameraWrapper = PhotoCameraWrapper(
            textureViewWrapper,
            rotation,
            requireFrontFacing = false
        )
        cameraInitialized = true
        cameraWrapper.resultObservable.subscribeManaged({ result ->
            onCameraResult(result)
        }, {
            throw it
        })

        adjustTextureView(textureView)
    }

    private fun onCameraResult(result: BaseCameraWrapper.BitmapCameraResult) {
        ivResult.show()
        ivResult.alpha = 1f
        ivResult.rotation = 0f
        ivResult.x = 0f
        ivResult.y = textureView.y
        ivResult.scaleX = 1f
        ivResult.scaleY = 1f

        ivResult.setImageBitmap(result.bitmap)

        val targetX = bGallery.x + bGallery.measuredWidth/2 - ivResult.measuredWidth/2
        val targetY = (lBottom.y + bGallery.y) + bGallery.measuredHeight/2 - ivResult.measuredHeight/2
        val targetScaleX = bGallery.measuredWidth.toFloat() / ivResult.measuredWidth * 0.6f
        val targetScaleY = bGallery.measuredHeight.toFloat() / ivResult.measuredHeight * 0.6f
        val startY = ivResult.y
        val anim = ValueAnimator.ofFloat(1f, 0f)
        anim.interpolator = INTERPOLATOR
        anim.addUpdateListener { valueAnimator ->
            val animatorValue = valueAnimator.animatedValue as Float
            ivResult.scaleX = (animatorValue * (1 - targetScaleX)) + targetScaleX
            ivResult.scaleY = (animatorValue * (1 - targetScaleY)) + targetScaleY
            ivResult.x = (1-animatorValue) * targetX
            ivResult.y = startY - ((1-animatorValue) * (startY - targetY))
            ivResult.rotation = (1-animatorValue) * -360
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
                result.bitmap.recycle()
            }
            .start()

        updateGallery()
    }

    private fun adjustTextureView(textureView: TextureView) {
        val scale = PreviewAspectRatio.ratio480to640 / PreviewAspectRatio.getForSurface480to640(
            DeviceInfoUtils.getModelAndManufacturer(),
            frontFacing = false
        )
        Logger.v(scale)
        textureView.scaleY = scale
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
            BiFunction<TextureViewWrapper.TextureState, Boolean,
                    Pair<TextureViewWrapper.TextureState, Boolean>> { p1, p2 ->
                Pair(p1, p2)
            }
        ).subscribeManaged { pair ->
            val textureState = pair.first
            val permissionsAllowed = pair.second
            if (permissionsAllowed) {
                when (textureState) {
                    is TextureViewWrapper.TextureCreated -> {
                        if (!cameraInitialized) {
                            initCamera()

                            bGallery.setOnClickListener {
                                if (lGallery.isVisible) {
                                    hideGallery()
                                } else {
                                    showGallery()
                                }
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
//        val images = GalleryUtils.fetchGalleryImages(this)
        val images = CustomApplication.context.externalCacheDir?.listFiles()?.toList()
            ?.map { it.absolutePath }
        galleryAdapter.updateItems(images ?: listOf())
        Logger.d(images)
    }

    private fun showGallery() {
        lGallery.y = DeviceInfoUtils.getScreenSize().height.toFloat()
        lGallery.show()
        lGallery.animate().y(0f).start()
    }

    private fun hideGallery() {
        lGallery.animate()
            .y(DeviceInfoUtils.getScreenSize().height.toFloat())
            .withEndAction {
                lGallery.hide()
            }
            .start()
    }

    companion object {
        const val ANIM_DURATION = 700L
        const val FADE_DURATION = 350L
        const val FADE_DELAY = 600L
        val INTERPOLATOR = android.view.animation.AccelerateDecelerateInterpolator()
    }
}