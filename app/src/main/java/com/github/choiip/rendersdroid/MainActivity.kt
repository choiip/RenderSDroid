package com.github.choiip.rendersdroid

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.os.AsyncTask
import android.renderscript.Allocation
import android.renderscript.RenderScript
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import kotlinx.android.synthetic.main.main_layout.*
import kotlinx.android.synthetic.main.function_layout.*
import android.widget.ArrayAdapter

class MainActivity : AppCompatActivity() {

    private var rs: RenderScript? = null
    /**
     * Number of bitmaps that is used for RenderScript thread and UI thread synchronization.
     */
    private val NUM_BITMAPS = 2
    private var mCurrentBitmap = 0
    private var mBitmapIn: Bitmap? = null
    private var mBitmapsOut: Array<Bitmap?> = arrayOfNulls(NUM_BITMAPS)

    private var mInAllocation: Allocation? = null
    private var mOutAllocations: Array<Allocation?> = arrayOfNulls(NUM_BITMAPS)
    private var mRenderSFunctions: Array<RenderSFunction?> = arrayOfNulls(3)
    private var mCurrentTask: RenderScriptTask? = null
    private var mCurrentFunction = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)

        // Initialize UI
        mBitmapIn = loadBitmap(R.drawable.tree)
        sourceImage!!.setImageBitmap(mBitmapIn)

        for (i in 0 until NUM_BITMAPS) {
            mBitmapsOut[i] = Bitmap.createBitmap(
                mBitmapIn!!.width,
                mBitmapIn!!.height, mBitmapIn!!.config
            )
        }

        mCurrentBitmap += (mCurrentBitmap + 1) % NUM_BITMAPS

        // Create renderScript
        createScript()

        // Setup function spinner
        setupFunctionSpinner()

        // Setup current function panel
        setupFunctionPanel()

        // Invoke renderScript kernel and update imageView
        updateImage()
    }

    override fun onDestroy() {
        rs?.destroy()
        super.onDestroy()
    }

    /**
     * Initialize RenderScript.
     */
    private fun createScript() {
        // Initialize RS
        rs = RenderScript.create(this)

        // Allocate buffers
        mInAllocation = Allocation.createFromBitmap(rs, mBitmapIn)

        for (i in 0 until NUM_BITMAPS) {
            mOutAllocations[i] = Allocation.createFromBitmap(rs, mBitmapsOut[i])
        }

        // Load renderscript functions
        mRenderSFunctions[0] = RenderSInvert(rs!!)
        mRenderSFunctions[1] = RenderSBlur(rs!!)
        mRenderSFunctions[2] = RenderSSaturation(rs!!)
    }

    private fun setupFunctionSpinner() {
        // Spinner click listener
        spinnerFunction.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent: AdapterView<*>?) {
                println("Nothing")
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (mCurrentFunction != position) {
                    mCurrentFunction = position
                    setupFunctionPanel()
                    updateImage()
                }
            }
        }

        val categories = ArrayList<String>()

        // Spinner Drop down elements
        mRenderSFunctions.forEach {
            categories.add(it?.name!!)
        }

        // Creating adapter for spinner
        val dataAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)

        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        // attaching data adapter to spinner
        spinnerFunction.adapter = dataAdapter
    }

    private fun setupFunctionPanel() {
        val functionLayout = mRenderSFunctions[mCurrentFunction]?.functionLayout()
        if (paramLayout.childCount > 0) {
            paramLayout.removeViewAt(0)
        }
        if (functionLayout != null) {
            val layoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            paramLayout.addView(layoutInflater.inflate(functionLayout, rootLayout, false), -1)
        }
        mRenderSFunctions[mCurrentFunction]?.initParamsUI(this, { -> updateImage() })
    }

    /*
     * In the AsyncTask, it invokes RenderScript intrinsics to do a filtering.
     * After the filtering is done, an operation blocks at Allocation.copyTo() in AsyncTask thread.
     * Once all operation is finished at onPostExecute() in UI thread, it can invalidate and update
     * ImageView UI.
     */
    private inner class RenderScriptTask : AsyncTask<Void, Void, Int>() {
        internal var issued: Boolean? = false

        override fun doInBackground(vararg params: Void?): Int {
            var index = -1
            if (!isCancelled) {
                issued = true
                index = mCurrentBitmap

                // Invoke saturation filter kernel
                mRenderSFunctions[mCurrentFunction]!!.invokeScript(mInAllocation, mOutAllocations[index])

                // Copy to bitmap and invalidate image view
                mOutAllocations[index]?.copyTo(mBitmapsOut[index])
                mCurrentBitmap = (mCurrentBitmap + 1) % NUM_BITMAPS
            }
            return index
        }

        internal fun updateView(result: Int) {
            if (result != -1) {
                // Request UI update
                resultImage!!.setImageBitmap(mBitmapsOut[result])
                resultImage!!.invalidate()
            }
        }

        override fun onPostExecute(result: Int) {
            updateView(result)
        }

        override fun onCancelled(result: Int) {
            if (issued!!) {
                updateView(result)
            }
        }
    }

    /**
     * Invoke AsyncTask and cancel previous task. When AsyncTasks are piled up (typically in slow
     * device with heavy kernel), Only the latest (and already started) task invokes RenderScript
     * operation.
     */
    private fun updateImage() {
        if (mCurrentTask != null) {
            mCurrentTask!!.cancel(false)
        }
        mCurrentTask = RenderScriptTask()
        mCurrentTask!!.execute()
    }

    /**
     * Helper to load Bitmap from resource
     */
    private fun loadBitmap(resource: Int): Bitmap {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeResource(resources, resource, options)
    }

}