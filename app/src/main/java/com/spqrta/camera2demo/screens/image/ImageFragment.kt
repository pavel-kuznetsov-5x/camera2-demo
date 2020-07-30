package com.spqrta.camera2demo.screens.image

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.spqrta.camera2demo.MainActivity
import com.spqrta.camera2demo.R
import com.spqrta.camera2demo.base.BaseFragment
import kotlinx.android.synthetic.main.fragment_image.*

class ImageFragment : BaseFragment<MainActivity>() {

    private val args: ImageFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bBack.setOnClickListener {
            mainActivity().onBackPressed()
        }
        Glide.with(this).load(args.imagePath).into(ivImage)
    }
}