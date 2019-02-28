package com.github.choiip.rendersdroid

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.os.AsyncTask
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import kotlinx.android.synthetic.main.main_layout.*
import kotlinx.android.synthetic.main.function_layout.*
import android.widget.ArrayAdapter
import com.otaliastudios.cameraview.*
import android.renderscript.*
import android.view.Surface

class MainActivity : AppCompatActivity() {

    private var mRS: RenderScript? = null
    /**
     * Number of bitmaps that is used for RenderScript thread and UI thread synchronization.
     */
    private val NUM_BITMAPS = 2
    private var mCurrentBitmap = 0
    private var mBitmapsOut: Array<Bitmap?> = arrayOfNulls(NUM_BITMAPS)
    private var mYUVToRGBIntrinsic: ScriptIntrinsicYuvToRGB? = null

    private var mInAllocation: Allocation? = null
    private var mOutAllocations: Array<Allocation?> = arrayOfNulls(NUM_BITMAPS)
    private var mRenderSFunctions: Array<RenderSFunction?> = arrayOfNulls(3)
    private var mCurrentTask: RenderScriptTask? = null
    private var mCurrentFunction = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        supportActionBar?.hide() // hide the title bar
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN) //enable full screen

        setContentView(R.layout.main_layout)

        // Initialize RS
        mRS = RenderScript.create(this)

        // Setup camera
        val desiredDimensions = Size(640, 480)
        setupCamera(desiredDimensions)

        setupInAllocation(mRS!!, desiredDimensions)

        // Initialize output bitmaps and allocations
        for (i in 0 until NUM_BITMAPS) {
            mBitmapsOut[i] = Bitmap.createBitmap(
                desiredDimensions.width, desiredDimensions.height, Bitmap.Config.ARGB_8888
            )
        }

        setupOutAllocation(mRS!!)
        resultImage!!.scaleY = -1.0f

        when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> { resultImage!!.rotation = 270.0f }
            Surface.ROTATION_90 -> { resultImage!!.rotation = 270.0f - 90.0f }
            Surface.ROTATION_180 -> { resultImage!!.rotation = 270.0f - 180.0f }
            Surface.ROTATION_270 -> { resultImage!!.rotation = 270.0f - 270.0f }
        }

        mCurrentBitmap += (mCurrentBitmap + 1) % NUM_BITMAPS

        // Create renderScript
        createScript(mRS!!)

        // Setup function spinner
        setupFunctionSpinner()

        // Setup current function panel
        setupFunctionPanel()

        // Invoke renderScript kernel and update imageView
        updateImage()
    }

    override fun onResume() {
        super.onResume()
        cameraView.open()
    }

    override fun onPause() {
        super.onPause()
        cameraView.close()
    }

    override fun onDestroy() {
        mRS?.destroy()
        super.onDestroy()
        cameraView.destroy()
    }

    private fun setupCamera(desiredDimensions: Size): Size {
        cameraView.setFacing(Facing.FRONT)

        var width = desiredDimensions.width
        var height = desiredDimensions.height
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            // swap width and height
            width = height.also { height = width }
        }

        val minWidth = SizeSelectors.minWidth(width)
        val minHeight = SizeSelectors.minHeight(height)
        val maxWidth = SizeSelectors.maxWidth(width)
        val maxHeight = SizeSelectors.maxHeight(height)
        val minDimensions = SizeSelectors.and(minWidth, minHeight) // Matches sizes bigger than minWidth x minHeight.
        val maxDimensions = SizeSelectors.and(maxWidth, maxHeight) // Matches sizes smaller than maxWidth x maxHeight.
        val dimensions = SizeSelectors.and(minDimensions, maxDimensions)
        val ratio = SizeSelectors.aspectRatio(AspectRatio.of(width, height), 0f) // Matches 4:3 sizes.

        val result = SizeSelectors.or(
            SizeSelectors.and(ratio, dimensions), // Try to match both constraints
            ratio, // If none is found, at least try to match the aspect ratio
            SizeSelectors.biggest() // If none is found, take the biggest
        )
        cameraView.setPreviewSize(result)

        cameraView.addFrameProcessor {
            if (it.data?.size != null) {    // this fixed the empty data crash issue in blackberry
                mInAllocation?.copyFromUnchecked(it.data)
                updateImage()
            }
        }
        return Size(width, height)
    }

    private fun setupInAllocation(rs: RenderScript, dimensions: Size) {
        val yuvDataLength = dimensions.width * dimensions.height * 3 / 2
        mInAllocation = Allocation.createSized(rs, Element.U8(rs), yuvDataLength)
    }

    private fun setupInAllocation(rs: RenderScript, bitmapIn: Bitmap) {
        // Allocate buffers
        mInAllocation = Allocation.createFromBitmap(rs, bitmapIn)
    }

    private fun setupOutAllocation(rs: RenderScript) {
        // Allocate buffers
        for (i in 0 until NUM_BITMAPS) {
            mOutAllocations[i] = Allocation.createFromBitmap(rs, mBitmapsOut[i])
        }
    }

    /**
     * Initialize RenderScript.
     */
    private fun createScript(rs: RenderScript) {
        mYUVToRGBIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.RGBA_8888(rs))
        mYUVToRGBIntrinsic?.setInput(mInAllocation)

        // Load renderscript functions
        mRenderSFunctions[0] = RenderSInvert(rs)
        mRenderSFunctions[1] = RenderSBlur(rs)
        mRenderSFunctions[2] = RenderSSaturation(rs)
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

                mYUVToRGBIntrinsic!!.forEach(mOutAllocations[index])
                // Invoke script
                mRenderSFunctions[mCurrentFunction]!!.invokeScript(mOutAllocations[index], mOutAllocations[index])

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
            if (mCurrentTask!!.status != AsyncTask.Status.FINISHED)
                return
//            mCurrentTask!!.cancel(false)
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