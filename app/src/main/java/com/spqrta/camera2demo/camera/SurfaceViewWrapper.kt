package com.spqrta.camera2demo.camera

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import io.reactivex.subjects.BehaviorSubject


class SurfaceViewWrapper(val surfaceView: SurfaceView) {

    val subject: BehaviorSubject<BaseCameraWrapper.SurfaceState> = BehaviorSubject.create<BaseCameraWrapper.SurfaceState>()

    init {
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                subject.onNext(BaseCameraWrapper.SurfaceAvailable(holder.surface))
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
                subject.onNext(BaseCameraWrapper.SurfaceDestroyed)
            }
        })
    }



}