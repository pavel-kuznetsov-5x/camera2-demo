package com.spqrta.camera2demo.screens.start

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.spqrta.camera2demo.MainActivity
import com.spqrta.camera2demo.R
import com.spqrta.camera2demo.base.BaseFragment
import kotlinx.android.synthetic.main.fragment_start.*

class StartFragment : BaseFragment<MainActivity>() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_start, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bTexture.setOnClickListener {
            findNavController().navigate(StartFragmentDirections.actionStartFragmentToTextureCameraFragment())
        }
    }
}