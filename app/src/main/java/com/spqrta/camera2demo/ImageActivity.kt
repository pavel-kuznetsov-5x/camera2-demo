package com.spqrta.camera2demo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.activity_image.*

class ImageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image)
        bBack.setOnClickListener {
            onBackPressed()
        }
        Glide.with(this).load(intent.getStringExtra(String::class.java.simpleName)).into(ivImage)
    }
}