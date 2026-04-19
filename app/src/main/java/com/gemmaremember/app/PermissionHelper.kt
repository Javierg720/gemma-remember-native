package com.gemmaremember.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object PermissionHelper {
    val CAMERA = Manifest.permission.CAMERA
    val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO
    val FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
    val COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasCamera(context: Context) = hasPermission(context, CAMERA)
    fun hasMic(context: Context) = hasPermission(context, RECORD_AUDIO)
    fun hasLocation(context: Context) = hasPermission(context, FINE_LOCATION) || hasPermission(context, COARSE_LOCATION)
}
