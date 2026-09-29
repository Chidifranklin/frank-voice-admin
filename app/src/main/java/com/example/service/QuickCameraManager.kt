package com.example.service

import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.util.Log

class QuickCameraManager(private val context: Context) {

    fun launchCameraForPhoto(): Pair<Boolean, String> {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Pair(true, "Opening camera to capture photo")
            } else {
                // Fallback to standard camera launcher
                val stillCameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(stillCameraIntent)
                Pair(true, "Launching camera")
            }
        } catch (e: Exception) {
            Log.e("QuickCameraManager", "Failed to launch camera: ${e.message}")
            Pair(false, "Could not open camera: ${e.message}")
        }
    }

    fun launchCameraForVideo(): Pair<Boolean, String> {
        return try {
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            Pair(true, "Opening camera to record video")
        } catch (e: Exception) {
            Log.e("QuickCameraManager", "Failed to launch video camera: ${e.message}")
            Pair(false, "Could not open video camera: ${e.message}")
        }
    }
}
