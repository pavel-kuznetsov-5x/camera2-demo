package com.spqrta.camera2demo.camera

import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.spqrta.camera2demo.screens.surface_camera.SurfaceCameraFragment
import com.spqrta.camera2demo.utility.Logg
import io.reactivex.subjects.BehaviorSubject


class SurfaceViewWrapper(val surfaceView: SurfaceView) {

    fun setSurfaceSize(size: Size) {
        surfaceView.holder?.setFixedSize(size.width, size.height)
        //todo
//        surfaceView.holder?.setFixedSize(2048, 1536)
    }

    val surface = surfaceView.holder.surface

    val subject: BehaviorSubject<SurfaceViewState> = BehaviorSubject.create<SurfaceViewState>()

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                subject.onNext(SurfaceAvailable)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int
            ) {
                //todo
//                subject.onNext()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                subject.onNext(SurfaceDestroyed)
            }
        })
    }

    open class SurfaceViewState
    object SurfaceAvailable : SurfaceViewState()
    object SurfaceDestroyed : SurfaceViewState()



}