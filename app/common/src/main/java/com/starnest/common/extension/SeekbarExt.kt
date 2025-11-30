package com.starnest.common.extension

import android.widget.SeekBar

fun SeekBar.doOnProgressChanged(
    onChange: (
        seekBar: SeekBar?,
        progress: Int,
        fromUser: Boolean
    ) -> Unit) {
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(
            seekBar: SeekBar?,
            progress: Int,
            fromUser: Boolean
        ) {
            onChange.invoke(seekBar, progress, fromUser)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
        }
    })
}