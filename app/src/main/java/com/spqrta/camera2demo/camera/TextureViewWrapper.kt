package com.spqrta.camera2demo.camera

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import io.reactivex.subjects.BehaviorSubject


class TextureViewWrapper(val textureView: TextureView) {

    val subject: BehaviorSubject<BaseCameraWrapper.PreviewSurfaceState> = BehaviorSubject.create<BaseCameraWrapper.PreviewSurfaceState>()

    init {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
//                surfaceTexture.setDefaultBufferSize(textureView.width, textureView.height)
                surfaceTexture.setDefaultBufferSize(240, 320)
                subject.onNext(BaseCameraWrapper.SurfaceAvailable(Surface(surfaceTexture)))
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture?,
                width: Int,
                height: Int
            ) {
                //todo
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
                //todo
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                subject.onNext(BaseCameraWrapper.SurfaceDestroyed)
                return true
            }
        }
    }

}