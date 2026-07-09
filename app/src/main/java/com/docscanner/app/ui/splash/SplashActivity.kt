package com.docscanner.app.ui.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.docscanner.app.databinding.ActivitySplashBinding
import com.docscanner.app.ui.main.MainActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.post { startSplashAnimations() }
        animateProgressBar(5000L) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun startSplashAnimations() {
        val logo = binding.splashLogo
        val glow = binding.splashLogoGlow

        logo.alpha = 0f
        logo.scaleX = 0.2f
        logo.scaleY = 0.2f
        logo.rotation = -18f
        glow.alpha = 0f
        glow.scaleX = 0.5f
        glow.scaleY = 0.5f

        binding.splashTitle.alpha = 0f
        binding.splashTitle.translationY = 50f
        binding.splashTagline.alpha = 0f
        binding.splashTagline.translationY = 30f

        binding.ringOuter.scaleX = 0.6f
        binding.ringOuter.scaleY = 0.6f
        binding.ringOuter.alpha = 0f
        binding.ringMid.scaleX = 0.5f
        binding.ringMid.scaleY = 0.5f
        binding.ringMid.alpha = 0f
        binding.ringInner.scaleX = 0.4f
        binding.ringInner.scaleY = 0.4f
        binding.ringInner.alpha = 0f

        binding.glowOrb1.scaleX = 0.5f
        binding.glowOrb1.scaleY = 0.5f
        binding.glowOrb2.scaleX = 1.4f
        binding.glowOrb2.scaleY = 1.4f
        binding.glowOrb2.alpha = 0f

        listOf(binding.particle1, binding.particle2, binding.particle3).forEach {
            it.alpha = 0f
        }

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, View.ALPHA, 0f, 1f).apply {
                    duration = 800
                },
                ObjectAnimator.ofFloat(logo, View.SCALE_X, 0.2f, 1.08f, 1f).apply {
                    duration = 1200
                    interpolator = OvershootInterpolator(2.2f)
                },
                ObjectAnimator.ofFloat(logo, View.SCALE_Y, 0.2f, 1.08f, 1f).apply {
                    duration = 1200
                    interpolator = OvershootInterpolator(2.2f)
                },
                ObjectAnimator.ofFloat(logo, View.ROTATION, -18f, 0f).apply {
                    duration = 1000
                    interpolator = AccelerateDecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(glow, View.ALPHA, 0f, 0.7f).apply {
                    startDelay = 200
                    duration = 900
                },
                ObjectAnimator.ofFloat(glow, View.SCALE_X, 0.5f, 1.3f).apply {
                    startDelay = 200
                    duration = 900
                },
                ObjectAnimator.ofFloat(glow, View.SCALE_Y, 0.5f, 1.3f).apply {
                    startDelay = 200
                    duration = 900
                }
            )
            start()
        }

        ObjectAnimator.ofFloat(binding.ringOuter, View.ALPHA, 0f, 1f).apply {
            startDelay = 300
            duration = 600
            start()
        }
        ObjectAnimator.ofFloat(binding.ringOuter, View.SCALE_X, 0.6f, 1f).apply {
            startDelay = 300
            duration = 800
            interpolator = OvershootInterpolator(1.4f)
            start()
        }
        ObjectAnimator.ofFloat(binding.ringOuter, View.SCALE_Y, 0.6f, 1f).apply {
            startDelay = 300
            duration = 800
            interpolator = OvershootInterpolator(1.4f)
            start()
        }
        ObjectAnimator.ofFloat(binding.ringMid, View.ALPHA, 0f, 0.9f).apply {
            startDelay = 450
            duration = 600
            start()
        }
        ObjectAnimator.ofFloat(binding.ringMid, View.SCALE_X, 0.5f, 1f).apply {
            startDelay = 450
            duration = 800
            interpolator = OvershootInterpolator(1.3f)
            start()
        }
        ObjectAnimator.ofFloat(binding.ringMid, View.SCALE_Y, 0.5f, 1f).apply {
            startDelay = 450
            duration = 800
            interpolator = OvershootInterpolator(1.3f)
            start()
        }
        ObjectAnimator.ofFloat(binding.ringInner, View.ALPHA, 0f, 0.85f).apply {
            startDelay = 550
            duration = 600
            start()
        }
        ObjectAnimator.ofFloat(binding.ringInner, View.SCALE_X, 0.4f, 1f).apply {
            startDelay = 550
            duration = 800
            interpolator = OvershootInterpolator(1.2f)
            start()
        }
        ObjectAnimator.ofFloat(binding.ringInner, View.SCALE_Y, 0.4f, 1f).apply {
            startDelay = 550
            duration = 800
            interpolator = OvershootInterpolator(1.2f)
            start()
        }

        ObjectAnimator.ofFloat(binding.ringOuter, View.ROTATION, 0f, 360f).apply {
            duration = 5000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(binding.ringMid, View.ROTATION, 360f, 0f).apply {
            duration = 3500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(binding.ringInner, View.ROTATION, 0f, -360f).apply {
            duration = 2800
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(binding.glowOrb2, View.ALPHA, 0f, 0.25f).apply {
            duration = 1000
            start()
        }
        ObjectAnimator.ofFloat(binding.glowOrb2, View.ROTATION, 0f, 360f).apply {
            duration = 12000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        val orbPulseX = ObjectAnimator.ofFloat(binding.glowOrb1, View.SCALE_X, 0.8f, 1.2f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        val orbPulseY = ObjectAnimator.ofFloat(binding.glowOrb1, View.SCALE_Y, 0.8f, 1.2f).apply {
            duration = 1600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        AnimatorSet().apply { playTogether(orbPulseX, orbPulseY); start() }

        startParticleOrbit(binding.particle1, 0f, 5200L)
        startParticleOrbit(binding.particle2, 120f, 6200L)
        startParticleOrbit(binding.particle3, 240f, 7000L)

        startLogoBreathing()

        ObjectAnimator.ofFloat(binding.splashTitle, View.ALPHA, 0f, 1f).apply {
            startDelay = 650
            duration = 700
            start()
        }
        ObjectAnimator.ofFloat(binding.splashTitle, View.TRANSLATION_Y, 50f, 0f).apply {
            startDelay = 650
            duration = 700
            interpolator = OvershootInterpolator(1.5f)
            start()
        }
        ObjectAnimator.ofFloat(binding.splashTagline, View.ALPHA, 0f, 0.9f).apply {
            startDelay = 900
            duration = 600
            start()
        }
        ObjectAnimator.ofFloat(binding.splashTagline, View.TRANSLATION_Y, 30f, 0f).apply {
            startDelay = 900
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startParticleOrbit(particle: View, startAngle: Float, duration: Long) {
        particle.alpha = 0f
        ObjectAnimator.ofFloat(particle, View.ALPHA, 0f, 1f, 0.6f, 1f).apply {
            this.duration = 1200
            startDelay = 400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        ValueAnimator.ofFloat(startAngle, startAngle + 360f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val angle = Math.toRadians((anim.animatedValue as Float).toDouble())
                val radius = 130f * resources.displayMetrics.density
                particle.translationX = (kotlin.math.cos(angle) * radius).toFloat()
                particle.translationY = (kotlin.math.sin(angle) * radius).toFloat()
            }
            start()
        }
    }

    private fun startLogoBreathing() {
        binding.splashLogo.postDelayed({
            val pulseX = ObjectAnimator.ofFloat(binding.splashLogo, View.SCALE_X, 1f, 1.05f).apply {
                duration = 1400
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }
            val pulseY = ObjectAnimator.ofFloat(binding.splashLogo, View.SCALE_Y, 1f, 1.05f).apply {
                duration = 1400
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }
            AnimatorSet().apply { playTogether(pulseX, pulseY); start() }
        }, 1300)
    }

    private fun animateProgressBar(durationMs: Long, onComplete: () -> Unit) {
        ValueAnimator.ofInt(0, 100).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                binding.splashProgress.progress = animator.animatedValue as Int
            }
            doOnEnd { onComplete() }
            start()
        }
    }
}
