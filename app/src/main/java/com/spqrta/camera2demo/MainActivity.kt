package com.spqrta.camera2demo

import android.Manifest
import android.app.Activity
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.spqrta.camera2demo.base.BaseActivity
import com.spqrta.camera2demo.utility.Logger
import com.spqrta.camera2demo.utility.recycler.ArrayRecyclerAdapter
import com.spqrta.camera2demo.utility.utils.DeviceInfoUtils
import com.spqrta.camera2demo.utility.utils.GalleryUtils
import com.spqrta.camera2demo.utility.utils.hide
import com.spqrta.camera2demo.utility.utils.show
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : BaseActivity() {

    override val layoutRes = R.layout.activity_main

    private val galleryAdapter = GalleryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bGallery.setOnClickListener {
            if(lGallery.isVisible) {
                hideGallery()
            } else {
                showGallery()
            }
        }

        //todo handle not granted
        RxPermissions(this).requestEach(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ).toList().subscribeManaged {
            updateGallery()
        }

        rvGallery.layoutManager = GridLayoutManager(this, 3)
        rvGallery.adapter = galleryAdapter
    }

    private fun updateGallery() {
        //todo handle empty
        val images = GalleryUtils.fetchGalleryImages(this)
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
}