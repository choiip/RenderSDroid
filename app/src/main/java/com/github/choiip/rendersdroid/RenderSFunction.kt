package com.github.choiip.rendersdroid

import android.renderscript.Allocation

abstract class RenderSFunction(val name: String) {
    init {
        println("$name created")
    }
    open fun initParamsUI(activity: MainActivity, updateResult: () -> Unit) {}
    open fun functionLayout() : Int? { return null }
    abstract fun invokeScript(ain: Allocation?, aout: Allocation?)
}