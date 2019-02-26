package com.github.choiip.rendersdroid

import android.renderscript.*
import android.widget.SeekBar

class RenderSSaturation(rs: RenderScript) : RenderSFunction("Saturation") {
    private var mScript: ScriptC_saturation? = null

    init {
        mScript = ScriptC_saturation(rs)
        mScript!!._saturationValue = 1.0f
    }

    override fun initParamsUI(activity: MainActivity, updateResult: () -> Unit) {
        val seekbar = activity.findViewById(R.id.seekBar_saturation) as SeekBar?
        seekbar?.progress = 50
        seekbar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                val max = 2.0f
                val min = 0.0f
                val f = ((max - min) * (progress / 100.0) + min).toFloat()
                mScript!!._saturationValue = f
                updateResult()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    override fun functionLayout() : Int? {
        return R.layout.saturation_layout
    }

    override fun invokeScript(ain: Allocation?, aout: Allocation?) {
        mScript!!.forEach_saturation(ain, aout)
    }
}