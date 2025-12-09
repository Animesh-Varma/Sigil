package dev.animeshvarma.sigil.util

import android.content.Context
import android.content.SharedPreferences

class SigilPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sigil_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }

    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
}