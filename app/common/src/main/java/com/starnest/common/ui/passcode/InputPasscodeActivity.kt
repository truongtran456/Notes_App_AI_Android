package com.starnest.common.ui.passcode

import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.starnest.common.R
import com.starnest.common.databinding.ActivityInputPasscodeBinding
import com.starnest.common.extension.startTranslateAnimation
import com.starnest.core.base.activity.BaseActivity
import com.starnest.core.base.viewmodel.BaseViewModel
import com.starnest.core.extension.gone
import com.starnest.core.extension.serializable
import com.starnest.core.extension.show
import com.starnest.data.common.datasource.AppSharePrefs
import com.starnest.domain.common.model.Constants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class InputPasscodeActivity : BaseActivity<ActivityInputPasscodeBinding, BaseViewModel>(
    BaseViewModel::class
) {
    val appSharePrefs by lazy { sharePrefs as AppSharePrefs }

    override fun layoutId(): Int = R.layout.activity_input_passcode

    private var numList = ArrayList<String>()
    private var imagePinCode = ArrayList<ImageView>()
    private var modePin = ModePin.CREATE
    private var newPassCode = ""

    override fun initialize() {
        intent.extras?.serializable<ModePin>(Constants.Intents.MODE_PIN_CODE)?.let {
            modePin = it
        }
        when (modePin) {
            ModePin.FORCE -> {
                binding.tvTitle.text =
                    this.getString(com.starnest.resources.R.string.enter_pin_code)
                binding.tvCancel.gone()
            }

            ModePin.CONFIRM, ModePin.TURN_OFF -> {
                binding.tvTitle.text =
                    this.getString(com.starnest.resources.R.string.enter_pin_code)
                binding.tvCancel.show()
            }

            ModePin.CREATE -> {
                binding.tvTitle.text =
                    this.getString(com.starnest.resources.R.string.create_your_passcode)
                binding.tvCancel.show()
            }

            ModePin.CHANGE -> {
                binding.tvTitle.text =
                    this.getString(com.starnest.resources.R.string.enter_old_pin_code)
                binding.tvCancel.show()
            }
        }

        imagePinCode.add(binding.ivPin1)
        imagePinCode.add(binding.ivPin2)
        imagePinCode.add(binding.ivPin3)
        imagePinCode.add(binding.ivPin4)
        initOnClick()
    }


    override fun onBack() {
        super.onBack()
    }

    private fun initOnClick() {
        with(binding) {
            tvCancel.setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
            tvNum0.setOnClickListener {
                handleInputNumber("0")
            }
            tvNum1.setOnClickListener {
                handleInputNumber("1")
            }
            tvNum2.setOnClickListener {
                handleInputNumber("2")
            }
            tvNum3.setOnClickListener {
                handleInputNumber("3")
            }
            tvNum4.setOnClickListener {
                handleInputNumber("4")
            }
            tvNum5.setOnClickListener {
                handleInputNumber("5")
            }
            tvNum6.setOnClickListener {
                handleInputNumber("6")
            }
            tvNum7.setOnClickListener {
                handleInputNumber("7")
            }
            tvNum8.setOnClickListener {
                handleInputNumber("8")
            }
            tvNum9.setOnClickListener {
                handleInputNumber("9")
            }
            tvDelete.setOnClickListener {
                if (numList.isNotEmpty()) {
                    numList.removeLastOrNull()
                    imagePinCode[numList.size].apply {
                        this.background =
                            ContextCompat.getDrawable(
                                this@InputPasscodeActivity,
                                R.drawable.ic_lock_circle_rounded
                            )
                        this.backgroundTintList = null
                    }
                }
            }
        }
    }

    private fun handleInputNumber(number: String = "") {
        if (numList.size < 4 && number.isNotEmpty()) {
            numList.add(number)
        }

        binding.apply {
            imagePinCode.forEach {
                it.background = ContextCompat.getDrawable(
                    this@InputPasscodeActivity,
                    R.drawable.ic_lock_circle_rounded
                )
                it.backgroundTintList = null
            }
        }

        for (i in numList.indices) {
            imagePinCode[i].background = ContextCompat.getDrawable(
                this@InputPasscodeActivity,
                com.starnest.core.R.drawable.bg_circle
            )
            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    "#FFC340".toColorInt(),
                    "#FF8400".toColorInt()
                )
            )

            gradientDrawable.shape = GradientDrawable.OVAL
            imagePinCode[i].background = gradientDrawable

        }

        if (numList.size == 4) {
            if (newPassCode.isNotEmpty()) {
                val confirmPassCode = numList.joinToString("")
                if (newPassCode == confirmPassCode) {
                    appSharePrefs.passcode = newPassCode
                    setResult(RESULT_OK)
                    finish()
                } else {
                    binding.tvError.show()
                    animateDecorate()
                }
            } else {
                handleEventFullPin()
            }
        }
    }

    private fun handleEventFullPin() {
        when (modePin) {
            ModePin.CREATE -> {
                newPassCode = numList[0] + numList[1] + numList[2] + numList[3]
                binding.tvTitle.text =
                    this@InputPasscodeActivity.getString(com.starnest.resources.R.string.confirm_your_pin_code)
                numList.clear()
                handleInputNumber()
            }

            ModePin.FORCE, ModePin.CONFIRM, ModePin.TURN_OFF -> {
                if (appSharePrefs.passcode == numList[0] + numList[1] + numList[2] + numList[3]) {
                    setResult(RESULT_OK)
                    finish()
                } else {
                    binding.tvError.show()
                    animateDecorate()
                }
            }

            ModePin.CHANGE -> {
                if (appSharePrefs.passcode == numList[0] + numList[1] + numList[2] + numList[3]) {
                    binding.tvTitle.text =
                        this@InputPasscodeActivity.getString(com.starnest.resources.R.string.enter_pin_code)
                    binding.tvError.gone()
                    numList.clear()
                    modePin = ModePin.CREATE
                    handleInputNumber()
                } else {
                    binding.tvError.show()
                    animateDecorate()
                }
            }
        }
    }


    private fun animateDecorate() {
        binding.llPin.startTranslateAnimation(timeStop = 500L)
        numList.clear()
        handleInputNumber()
    }

    override fun onResume() {
        super.onResume()
        synchronized(lock) {
            instance?.let {
                if (it !== this) {
                    it.finish()
                }
            }
            instance = this
        }
    }

    override fun onDestroy() {
        synchronized(lock) {
            if (instance == this) {
                instance = null
            }
        }
        super.onDestroy()
    }

    companion object {
        private var instance: InputPasscodeActivity? = null
        private val lock = Any()
    }
}

enum class ModePin {
    CREATE, FORCE, CHANGE, CONFIRM, TURN_OFF
}