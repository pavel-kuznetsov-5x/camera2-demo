package com.spqrta.camera2demo.utility.utils

import android.app.Activity
import android.database.Cursor
import android.graphics.Bitmap
import android.os.Environment
import android.os.Environment.getDownloadCacheDirectory
import android.provider.MediaStore
import androidx.core.os.EnvironmentCompat
import com.spqrta.camera2demo.utility.CustomApplication
import com.spqrta.camera2demo.utility.Logger
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.File

object GalleryUtils {

    fun fetchGalleryImages(context: Activity): List<String>? {
        val columns = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media._ID
        ) //get all columns of type images
        val orderBy = MediaStore.Images.Media.DATE_ADDED //order data by date
        val imagecursor: Cursor = context.managedQuery(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, columns, null,
            null, "$orderBy DESC"
        ) //get all data in Cursor by sorting in DESC order
        val galleryImageUrls: ArrayList<String> = ArrayList()
        for (i in 0 until imagecursor.count) {
            imagecursor.moveToPosition(i)
            val dataColumnIndex: Int =
                imagecursor.getColumnIndex(MediaStore.Images.Media.DATA) //get column index
            galleryImageUrls.add(imagecursor.getString(dataColumnIndex)) //get Image from column index
        }
        return galleryImageUrls
    }

    //todo
    fun addToGallery(bitmap: Bitmap, title: String? = null) {
//        val filename = title ?: LocalDateTime.now().format(
//            DateTimeFormatter.ISO_DATE_TIME
//        ) + ".jpg"
//        val filePath = CustomApplication.context.externalCacheDir?.absolutePath + "/" + filename
//        Logger.d(filePath)
//        BitmapUtils.toFile(filePath, bitmap, quality = 95)
//        MediaStore.Images.Media.insertImage(CustomApplication.context.contentResolver, bitmap,  , "")
    }


    private fun getGalleryPath(): String? {
        val folder: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        if (!folder.exists()) {
            folder.mkdir()
        }
        return folder.absolutePath
    }


}