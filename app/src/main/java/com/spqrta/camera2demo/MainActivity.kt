package com.spqrta.camera2demo

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Bundle
import android.view.TextureView
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.spqrta.camera2demo.base.BaseActivity
import com.spqrta.camera2demo.base.NavActivity
import com.spqrta.camera2demo.camera.BaseCameraWrapper
import com.spqrta.camera2demo.camera.PhotoCameraWrapper
import com.spqrta.camera2demo.camera.TextureViewWrapper
import com.spqrta.camera2demo.utility.CustomApplication
import com.spqrta.camera2demo.utility.Logger
import com.spqrta.camera2demo.utility.Meter
import com.spqrta.camera2demo.utility.utils.*
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.layout_gallery.*
import java.io.File


class MainActivity : NavActivity() {


}