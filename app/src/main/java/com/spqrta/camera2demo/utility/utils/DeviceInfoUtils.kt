package com.spqrta.camera2demo.utility.utils

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.util.Size
import com.spqrta.camera2demo.utility.CustomApplication


@SuppressLint("DefaultLocale")
object DeviceInfoUtils {

    fun getScreenSize(): Size {
        return Size(
            Resources.getSystem().displayMetrics.widthPixels,
            Resources.getSystem().displayMetrics.heightPixels
        )
    }

    fun getModel(): String {
        return Build.MODEL.toUpperCase()
    }

    fun getManufacturer(): String {
        return Build.MANUFACTURER.toUpperCase()
    }

    fun getModelAndManufacturer(): String {
        return "${getManufacturer()} ### ${getModel()}"
    }

    const val SAMSUNG_A20_A205 = "SAMSUNG ### SM-A205FN"
    const val SAMSUNG_A20_A207 = "SAMSUNG ### SM-A207F"
    const val HUAWEI_ELE_L29 = "HUAWEI ### ELE-L29"

}