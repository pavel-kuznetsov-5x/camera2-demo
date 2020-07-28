package com.spqrta.camera2demo.camera

import android.graphics.SurfaceTexture
import android.view.TextureView
import com.spqrta.camera2demo.MainActivity
import io.reactivex.subjects.BehaviorSubject


class TextureViewWrapper(val textureView: TextureView) {

    val subject: BehaviorSubject<TextureState> = BehaviorSubject.create<TextureState>()

    init {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                surface.setDefaultBufferSize(textureView.width, textureView.height)
                subject.onNext(
                    TextureCreated(
                        surface
                    )
                )
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture?,
                width: Int,
                height: Int
            ) {
                //todo change matrix?
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
                //todo
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                subject.onNext(TextureDestroyed())
                return true
            }
        }
    }

    open class TextureState()
    class TextureCreated(val surfaceTexture: SurfaceTexture) : TextureState()
    class TextureDestroyed : TextureState()

}