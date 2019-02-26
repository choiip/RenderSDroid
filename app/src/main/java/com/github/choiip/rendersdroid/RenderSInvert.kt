package com.github.choiip.rendersdroid

import android.renderscript.*

class RenderSInvert(rs: RenderScript) : RenderSFunction("Invert") {
    private var mScript: ScriptC_invert? = null

    init {
        mScript = ScriptC_invert(rs)
    }

    override fun invokeScript(ain: Allocation?, aout: Allocation?) {
        mScript!!.forEach_invert(ain, aout)
    }
}