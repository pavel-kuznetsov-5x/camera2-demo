package com.spqrta.camera2demo.utility.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri

object IntentUtils {
    @SuppressLint("MissingPermission")
    fun initiateCall(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"));
        context.startActivity(intent)
    }

}