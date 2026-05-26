package com.example.autoclick.config

import android.content.Context
import android.content.SharedPreferences

object CoordinateStore {
    private const val PREFS_NAME = "coordinate_prefs"
    private const val KEY_LINGQU_X = "lingqu_x"
    private const val KEY_LINGQU_Y = "lingqu_y"
    private const val KEY_JIANGLI_X = "jiangli_x"
    private const val KEY_JIANGLI_Y = "jiangli_y"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveLingqu(x: Float, y: Float) {
        prefs.edit().putFloat(KEY_LINGQU_X, x).putFloat(KEY_LINGQU_Y, y).apply()
    }

    fun saveJiangli(x: Float, y: Float) {
        prefs.edit().putFloat(KEY_JIANGLI_X, x).putFloat(KEY_JIANGLI_Y, y).apply()
    }

    fun getLingqu(): Pair<Float, Float> {
        return Pair(prefs.getFloat(KEY_LINGQU_X, 0f), prefs.getFloat(KEY_LINGQU_Y, 0f))
    }

    fun getJiangli(): Pair<Float, Float> {
        return Pair(prefs.getFloat(KEY_JIANGLI_X, 0f), prefs.getFloat(KEY_JIANGLI_Y, 0f))
    }
}