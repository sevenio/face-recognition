@file:Suppress("DEPRECATION")

package com.tvisha.livenessdetect

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Pair
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.tvisha.featureRegister.RegisterFaceActivity
import com.tvisha.featureRegister.SimilarityClassifier
import com.tvisha.livenessdetect.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

@ObsoleteCoroutinesApi
class MainActivity : AppCompatActivity(), SetThresholdDialogFragment.ThresholdDialogListener {

    private lateinit var binding: ActivityMainBinding

    private var enginePrepared: Boolean = false
    private lateinit var engineWrapper: EngineWrapper
    private var threshold: Float = defaultThreshold

    private var camera: Camera? = null
    private var cameraId: Int = Camera.CameraInfo.CAMERA_FACING_FRONT
    private val previewWidth: Int = 640
    private val previewHeight: Int = 480

    /**
     *    1       2       3       4        5          6          7            8
     * <p>
     * 888888  888888      88  88      8888888888  88                  88  8888888888
     * 88          88      88  88      88  88      88  88          88  88      88  88
     * 8888      8888    8888  8888    88          8888888888  8888888888          88
     * 88          88      88  88
     * 88          88  888888  888888
     */
    private val frameOrientation: Int = 7

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var factorX: Float = 0F
    private var factorY: Float = 0F

    private val detectionContext = newSingleThreadContext("detection")
    private var working: Boolean = false

    private lateinit var scaleAnimator: ObjectAnimator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (hasPermissions()) {
            init()
        } else {
            requestPermission()
        }
        binding.recognize.setOnClickListener {
            isButtonClicked = true
        }
        binding.selectUser.setOnClickListener {
            displaynameListview()
        }
    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (permission in permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
        }
        return true
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun requestPermission() = requestPermissions(permissions, permissionReqCode)

    var isButtonClicked = false

    private fun init() {
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.result = DetectionResult()

        calculateSize()
        binding.register.setOnClickListener {
            startActivity(Intent(this@MainActivity, RegisterFaceActivity::class.java))
        }
        binding.surface.holder.let {
            it.setFormat(ImageFormat.NV21)
            it.addCallback(object : SurfaceHolder.Callback, Camera.PreviewCallback {
                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    if (holder?.surface == null) return

                    if (camera == null) return

                    try {
                        camera?.stopPreview()
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                    }

                    val parameters = camera?.parameters
                    parameters?.setPreviewSize(previewWidth, previewHeight)

                    factorX = screenWidth / previewHeight.toFloat()
                    factorY = screenHeight / previewWidth.toFloat()

                    camera?.parameters = parameters

                    camera?.startPreview()
                    camera?.setPreviewCallback(this)

                    setCameraDisplayOrientation()
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    camera?.setPreviewCallback(null)
                    camera?.release()
                    camera = null
                }

                override fun surfaceCreated(holder: SurfaceHolder) {
                    try {
                        camera = Camera.open(cameraId)
                    } catch (e: Exception) {
                        cameraId = Camera.CameraInfo.CAMERA_FACING_BACK
                        camera = Camera.open(cameraId)
                    }

                    try {
                        camera!!.setPreviewDisplay(binding.surface.holder)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }

                override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
                    if (enginePrepared && data != null) {
                        if (!working) {
                            GlobalScope.launch(detectionContext) {
                                working = true
                                val result = engineWrapper.detect(
                                    data,
                                    previewWidth,
                                    previewHeight,
                                    frameOrientation
                                )
                                result.threshold = threshold

                                val rect = calculateBoxLocationOnScreen(
                                    result.left,
                                    result.top,
                                    result.right,
                                    result.bottom
                                )

                                binding.result = result.updateLocation(rect)
                                if (isButtonClicked) {

                                    isButtonClicked = false
                                    startDetectingFace(result, data)

                            }
                                Log.d(
                                    tag,
                                    "threshold:${result.threshold}, confidence: ${result.confidence}"
                                )

                                binding.rectView.postInvalidate()
                                working = false
                            }
                        }
                    }
                }
            })
        }

        scaleAnimator = ObjectAnimator.ofFloat(binding.scan, View.SCALE_Y, 1F, -1F, 1F).apply {
            this.duration = 3000
            this.repeatCount = ValueAnimator.INFINITE
            this.repeatMode = ValueAnimator.REVERSE
            this.interpolator = LinearInterpolator()
            this.start()
        }

    }


    private fun startDetectingFace(result: DetectionResult, data: ByteArray){
        if (result.confidence > threshold){

            Log.d(
                "ganga", "button clicked"
            )
            Log.d(
                "ganga",
                "threshold:${result.threshold}, confidence: ${result.confidence}"
            )
            val inputImage = InputImage.fromByteArray(
                data,
                previewWidth,
                previewHeight,
                0,
                InputImage.IMAGE_FORMAT_NV21
            )
            lifecycleScope.async(Dispatchers.Main) {
                val out = ByteArrayOutputStream()
                val yuvImage = YuvImage(
                    data,
                    ImageFormat.NV21,
                    previewWidth,
                    previewHeight,
                    null
                )
                yuvImage.compressToJpeg(
                    Rect(
                        0,
                        0,
                        previewWidth,
                        previewHeight
                    ), 50, out
                )
                val imageBytes: ByteArray = out.toByteArray()
                val image: Bitmap = BitmapFactory.decodeByteArray(
                    imageBytes,
                    0,
                    imageBytes.size
                )
                detectFaces(rotateImage(image, 270.0F))
            }
//                                    setImage(inputImage)
        }else {
            lifecycleScope.async (Dispatchers.Main){
                Toast.makeText(
                    this@MainActivity,
                    "User is Spoofing",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private fun detectFaces(bitmap: Bitmap){
        val highAccuracyOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val detector = FaceDetection.getClient(highAccuracyOpts)
        val result = detector.process(
            inputImage
        )
            .addOnSuccessListener { faces ->
                if (faces.size != 0) {
                    val face = faces[0] //Get first face from detected faces
                    //                                                    System.out.println(face);

                    //mediaImage to Bitmap
//                    val frame_bmp: Bitmap = toBitmap(mediaImage!!)
//                    val rot = imageProxy.imageInfo.rotationDegrees

                    //Adjust orientation of Face
//                    val frame_bmp1: Bitmap =
//                        rotateBitmap(frame_bmp, rot, false, false)


                    //Get bounding box of face
                    val boundingBox = RectF(face.boundingBox)

                    //Crop out bounding box from whole Bitmap(image)
                    var cropped_face: Bitmap =
                        getCropBitmapByCPU(bitmap, boundingBox)

//                        if (flipX) cropped_face =
//                            rotateBitmap(cropped_face, 0, flipX, false)
                    //Scale the acquired Face to 112*112 which is required input for model
                    val scaled: Bitmap = getResizedBitmap(cropped_face, 112, 112)
                    binding.image.setImageBitmap(scaled)
                    recognizeImage(scaled)
                //Send scaled bitmap to create face embeddings.
                    //                                                    System.out.println(boundingBox);
                } else {
//                        if (registered.isEmpty()) reco_name.setText("Add Face") else reco_name.setText(
//                            "No Face Detected!"
//                        )
                }
            }
            .addOnFailureListener {
                // Task failed with an exception
                // ...
            }
            .addOnCompleteListener {
//                imageProxy.close() //v.important to acquire next frame for analysis
            }
    }
    lateinit var embeedings: Array<FloatArray>

    var inputSize = 112 //Input size for model
    lateinit var intValues: IntArray
    var isModelQuantized = false
    var IMAGE_MEAN = 128.0f
    var IMAGE_STD = 128.0f
    var OUTPUT_SIZE = 192

    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity, MODEL_FILE: String): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    private fun readFromSP(): java.util.HashMap<String, SimilarityClassifier.Recognition> {
        val sharedPreferences = getSharedPreferences("HashMap", Context.MODE_PRIVATE)
        val defValue = Gson().toJson(java.util.HashMap<String, SimilarityClassifier.Recognition>())
        val json = sharedPreferences.getString("map", defValue)
        // System.out.println("Output json"+json.toString());
        val token: TypeToken<java.util.HashMap<String, SimilarityClassifier.Recognition>> =
            object : TypeToken<java.util.HashMap<String, SimilarityClassifier.Recognition>>() {}
        val retrievedMap: java.util.HashMap<String, SimilarityClassifier.Recognition> =
            Gson().fromJson<java.util.HashMap<String, SimilarityClassifier.Recognition>>(
                json,
                token.type
            )
        // System.out.println("Output map"+retrievedMap.toString());

        //During type conversion and save/load procedure,format changes(eg float converted to double).
        //So embeddings need to be extracted from it in required format(eg.double to float).
        for ((_, value) in retrievedMap) {
            val output = Array(1) {
                FloatArray(
                    OUTPUT_SIZE
                )
            }
            var arrayList = value.getExtra() as ArrayList<*>
            arrayList = arrayList[0] as ArrayList<*>
            for (counter in arrayList.indices) {
                output[0][counter] = (arrayList[counter] as Double).toFloat()
            }
            value.setExtra(output)

            //System.out.println("Entry output "+entry.getKey()+" "+entry.getValue().getExtra() );
        }
        //        System.out.println("OUTPUT"+ Arrays.deepToString(outut));
//        Toast.makeText(this@MainActivity, "Recognitions Loaded", Toast.LENGTH_SHORT).show()
        return retrievedMap
    }
    var selectedUser = ""
    private fun displaynameListview() {
        val registered = readFromSP()
        val builder = AlertDialog.Builder(this@MainActivity)
        // System.out.println("Registered"+registered);
        if (registered.isEmpty()) builder.setTitle("No Faces Added!!") else builder.setTitle("Recognitions:")

        // add a checkbox list
        val names = arrayOfNulls<String>(registered.size)
        val checkedItems = BooleanArray(registered.size)
        var i = 0
        for ( key in registered.keys) {
            //System.out.println("NAME"+entry.getKey());
            names[i] = key
            checkedItems[i] = false
            i = i + 1
        }
        builder.setItems(names,object : DialogInterface.OnClickListener{
            override fun onClick(p0: DialogInterface?, p1: Int) {
                Log.d("ganga", names[p1]?:"nothing")
                selectedUser = names[p1]?:""
            }

        })
        builder.setPositiveButton(
            "OK"
        ) { dialog, which ->


        }


        // create and show the alert dialog
        val dialog = builder.create()
        dialog.show()
    }
    fun recognizeImage(bitmap: Bitmap) {

        // set Face to Preview
//        face_preview.setImageBitmap(bitmap)

        //Create ByteBuffer to store normalized image
        val imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())
        intValues = IntArray(inputSize * inputSize)

        //get pixel values from Bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        imgData.rewind()
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue: Int = intValues.get(i * inputSize + j)
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((pixelValue shr 16 and 0xFF).toByte())
                    imgData.put((pixelValue shr 8 and 0xFF).toByte())
                    imgData.put((pixelValue and 0xFF).toByte())
                } else { // Float model
                    imgData.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }
        //imgData is input to our model
        val inputArray = arrayOf<Any>(imgData)
        val outputMap: MutableMap<Int, Any> = HashMap()
        embeedings =
            Array<FloatArray>(1) { FloatArray(OUTPUT_SIZE) } //output of model will be stored in this variable
        outputMap[0] = embeedings

        lateinit var tfLite : Interpreter
        //Load model
        try {
            tfLite = Interpreter(loadModelFile(this@MainActivity,
                "mobilefacenet.tflite" //model name
            ))
        } catch (e: IOException) {
            e.printStackTrace()
        }
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap) //Run model
        var distance_local = Float.MAX_VALUE
        val id = "0"
        val label = "?"

        val registered = readFromSP()
        //Compare new face with saved Faces.
        if (registered.size > 0) {
            val nearest: List<Pair<String, Float>?> =
                findNearest(embeedings.get(0), registered) //Find 2 closest matching face
            if (nearest[0] != null) {
                if(nearest[0]!!.second < 0.5) {
                    val name = nearest[0]!!.first
                    Toast.makeText(this@MainActivity, "User is $name", Toast.LENGTH_LONG).show()
                    Log.d("ganga", name)
                }else {
                    Toast.makeText(this@MainActivity, "User not recognized", Toast.LENGTH_LONG).show()
                }
                //get name and distance of closest matching face
                // label = name;
//                distance_local = nearest[0]!!.second
//                if (developerMode) {
//                    if (distance_local < distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
//                        reco_name.setText(
//                            """
//                        Nearest: $name
//                        Dist: ${String.format("%.3f", distance_local)}
//                        2nd Nearest: ${nearest[1]!!.first}
//                        Dist: ${String.format("%.3f", nearest[1]!!.second)}
//                        """.trimIndent()
//                        ) else reco_name.setText(
//                        """
//                        Unknown
//                        Dist: ${String.format("%.3f", distance_local)}
//                        Nearest: $name
//                        Dist: ${String.format("%.3f", distance_local)}
//                        2nd Nearest: ${nearest[1]!!.first}
//                        Dist: ${String.format("%.3f", nearest[1]!!.second)}
//                        """.trimIndent()
//                    )
//
////                    System.out.println("nearest: " + name + " - distance: " + distance_local);
//                } else {
//                    if (distance_local < distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
//                        reco_name.setText(name) else reco_name.setText("Unknown")
//                    //                    System.out.println("nearest: " + name + " - distance: " + distance_local);
//                }
            }
        }


//            final int numDetectionsOutput = 1;
//            final ArrayList<SimilarityClassifier.Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
//            SimilarityClassifier.Recognition rec = new SimilarityClassifier.Recognition(
//                    id,
//                    label,
//                    distance);
//
//            recognitions.add( rec );
    }

    //    public void register(String name, SimilarityClassifier.Recognition rec) {
    //        registered.put(name, rec);
    //    }
    private fun findNearest(emb: FloatArray, registered: java.util.HashMap<String, SimilarityClassifier.Recognition>): List<Pair<String, Float>?> {
        val neighbour_list: MutableList<Pair<String, Float>?> = java.util.ArrayList()
        var ret: Pair<String, Float>? = null //to get closest match
        var prev_ret: Pair<String, Float>? = null //to get second closest match
        for ((name, value) in registered.entries) {

            val knownEmb = (value.getExtra() as Array<FloatArray>)[0]
            var distance = 0f
            for (i in emb.indices) {
                val diff = emb[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = Math.sqrt(distance.toDouble()).toFloat()
            if (ret == null || distance < ret.second) {
                prev_ret = ret
                ret = Pair<String, Float>(name, distance)
            }
            Log.d("ganga", "distance $distance name $name")
        }
        if (prev_ret == null) prev_ret = ret
        neighbour_list.add(ret)
        neighbour_list.add(prev_ret)
        return neighbour_list
    }
    fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)

        // "RECREATE" THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        bm.recycle()
        return resizedBitmap
    }

    private fun getCropBitmapByCPU(source: Bitmap?, cropRectF: RectF): Bitmap {
        val resultBitmap = Bitmap.createBitmap(
            cropRectF.width().toInt(),
            cropRectF.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val cavas = Canvas(resultBitmap)

        // draw background
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.WHITE
        cavas.drawRect(
            RectF(0f, 0f, cropRectF.width(), cropRectF.height()),
            paint
        )
        val matrix = Matrix()
        matrix.postTranslate(-cropRectF.left, -cropRectF.top)
        cavas.drawBitmap(source!!, matrix, paint)
        if (source != null && !source.isRecycled) {
            source.recycle()
        }
        return resultBitmap
    }
    private fun calculateSize() {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(dm)
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
    }
    fun rotateImage(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height,
            matrix, true
        )
    }

    private fun calculateBoxLocationOnScreen(left: Int, top: Int, right: Int, bottom: Int): Rect =
        Rect(
            (left * factorX).toInt(),
            (top * factorY).toInt(),
            (right * factorX).toInt(),
            (bottom * factorY).toInt()
        )

    private fun setCameraDisplayOrientation() {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        val rotation = windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        camera!!.setDisplayOrientation(result)
    }

    fun setting(@Suppress("UNUSED_PARAMETER") view: View) =
        SetThresholdDialogFragment().show(supportFragmentManager, "dialog")

    override fun onDialogPositiveClick(t: Float) {
        threshold = t
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionReqCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                init()
            } else {
                Toast.makeText(this, "请授权相机权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        engineWrapper = EngineWrapper(assets)
        enginePrepared = engineWrapper.init()

        if (!enginePrepared) {
            Toast.makeText(this, "Engine init failed.", Toast.LENGTH_LONG).show()
        }

        super.onResume()
    }

    override fun onDestroy() {
        engineWrapper.destroy()
        scaleAnimator.cancel()
        super.onDestroy()
    }

    companion object {
        const val tag = "MainActivity"
        const val defaultThreshold = 0.915F/*0.7F*/

        val permissions: Array<String> = arrayOf(Manifest.permission.CAMERA)
        const val permissionReqCode = 1
    }

}
