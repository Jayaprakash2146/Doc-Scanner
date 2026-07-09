package com.docscanner.app.ui.utils

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import com.docscanner.app.R

object UiEffects {

    /** Reliable click binding — use instead of applyFloatingPress for tool buttons. */
    fun bindClick(view: View, onClick: () -> Unit) {
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { onClick() }
    }

    fun applyFloatingPress(view: View, onClick: () -> Unit) {
        bindClick(view) {
            view.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(80)
                .withEndAction {
                    onClick()
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160)
                        .start()
                }
                .start()
        }
    }

    fun pulseGlow(view: View) {
        view.startAnimation(AnimationUtils.loadAnimation(view.context, R.anim.glow_pulse))
    }

    fun rotateSlow(view: View) {
        ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).apply {
            duration = 8000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    fun slideUpFade(view: View, delayMs: Long = 0) {
        view.alpha = 0f
        view.translationY = 48f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delayMs)
            .setDuration(420)
            .setInterpolator(OvershootInterpolator(1.4f))
            .start()
    }

    fun bounceIn(view: View) {
        view.scaleX = 0.3f
        view.scaleY = 0.3f
        view.alpha = 0f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator(2f))
            .start()
    }

    fun shimmerElevation(view: View) {
        val holder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Z, 4f, 18f, 4f)
        ObjectAnimator.ofPropertyValuesHolder(view, holder).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }
}
