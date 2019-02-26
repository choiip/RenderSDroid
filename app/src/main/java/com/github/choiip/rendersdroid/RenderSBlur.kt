package com.github.choiip.rendersdroid

import android.renderscript.*
import android.widget.SeekBar

class RenderSBlur(rs: RenderScript) : RenderSFunction("Blur") {
    private var mScript: ScriptIntrinsicBlur? = null

    init {
        mScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        mScript!!.setRadius(10.0f)
    }

    override fun initParamsUI(activity: MainActivity, updateResult: () -> Unit) {
        val seekbar = activity.findViewById(R.id.seekBar_blurRadius) as SeekBar?
        seekbar?.progress = 50
        seekbar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                val max = 20.0f
                val min = 0.01f
                val f = ((max - min) * (progress / 100.0) + min).toFloat()
                mScript!!.setRadius(f)
                updateResult()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    override fun functionLayout() : Int? {
        return R.layout.blur_layout
    }

    override fun invokeScript(ain: Allocation?, aout: Allocation?) {
        mScript!!.setInput(ain)
        mScript!!.forEach(aout)
    }
}