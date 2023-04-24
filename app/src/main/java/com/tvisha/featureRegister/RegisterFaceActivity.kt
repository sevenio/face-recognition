package com.tvisha.featureRegister

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.Image
import android.os.Bundle
import android.text.InputType
import android.util.Size
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.tvisha.MobileFaceNet
import com.tvisha.livenessdetect.databinding.ActivityRegisterBinding
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.ReadOnlyBufferException
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.experimental.inv


class RegisterFaceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private var cam_face = CameraSelector.LENS_FACING_FRONT //Default Back Camera
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    var modelFile = "mobilefacenet.tflite" //model name


    var tfLite: Interpreter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        registered = readFromSP() //Load saved faces from memory when app starts


        setContentView(binding.root)

        binding.ivFlip.setOnClickListener {
            flipCamera()
        }

        binding.btnRegister.setOnClickListener {
            addUser()
        }

        binding.btnActions.setOnClickListener {
            val builder = AlertDialog.Builder(this@RegisterFaceActivity)
            builder.setTitle("Select Action:")

            // add a checkbox list

            // add a checkbox list
            val names = arrayOf(
                "View Recognition List",
/*
                "Update Recognition List",
*/
               /* "Save Recognitions",
                "Load Recognitions",
                "Clear All Recognitions",
                "Import Photo (Beta)",*/
/*
                "Hyperparameters",
*/
/*
                "Developer Mode"
*/
            )

            builder.setItems(
                names
            ) { dialog, which ->
                when (which) {
                    0 -> displaynameListview()
                  /*  1 -> updatenameListview()
                    2 -> insertToSP(registered, 0) //mode: 0:save all, 1:clear all, 2:update all
                    3 -> registered.putAll(readFromSP())
                    4 -> clearnameList()
                    5 -> loadphoto()
                    6 -> testHyperparameter()
                    7 -> developerMode()*/
                }
            }


            builder.setPositiveButton(
                "OK"
            ) { dialog, which -> }
            builder.setNegativeButton("Cancel", null)

            // create and show the alert dialog

            // create and show the alert dialog
            val dialog = builder.create()
            dialog.show()
        }

        //Load model
        try {
            tfLite = Interpreter(loadModelFile(this@RegisterFaceActivity, modelFile))
        } catch (e: IOException) {
            e.printStackTrace()
        }
        //Initialize Face Detector
        //Initialize Face Detector
        val highAccuracyOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        detector = FaceDetection.getClient(highAccuracyOpts)
        cameraBind()
    }

    private fun displaynameListview() {
        val builder = AlertDialog.Builder(this@RegisterFaceActivity)
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
        builder.setItems(names, null)
        builder.setPositiveButton(
            "OK"
        ) { dialog, which -> }

        // create and show the alert dialog
        val dialog = builder.create()
        dialog.show()
    }

    @Throws(IOException::class)
    private fun loadModelFile(activity: Activity, MODEL_FILE: String): MappedByteBuffer {
        val fileDescriptor = activity.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun flipCamera() {
        cam_face = if (cam_face == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
            //            flipX = true
        } else {
            CameraSelector.LENS_FACING_BACK
            //            flipX = false
        }
        cameraProvider?.unbindAll()
        cameraBind()
    }

    var cameraProvider: ProcessCameraProvider? = null

    private fun cameraBind() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)


        cameraProviderFuture?.addListener(Runnable {
            try {
                cameraProvider = cameraProviderFuture?.get()
                cameraProvider?.let { bindPreview(it) }
            } catch (e: ExecutionException) {
                // No errors need to be handled for this in Future.
                // This should never be reached.
            } catch (e: InterruptedException) {
            }
        }, ContextCompat.getMainExecutor(this))
    }

    var cameraSelector: CameraSelector? = null
    var detector: FaceDetector? = null
    private fun toBitmap(image: Image): Bitmap {
        val nv21: ByteArray = YUV_420_888toNV21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
        val imageBytes = out.toByteArray()
        //System.out.println("bytes"+ Arrays.toString(imageBytes));

        //System.out.println("FORMAT"+image.getFormat());
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cam_face)
            .build()
        preview.setSurfaceProvider(binding.ivPreview.surfaceProvider)
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Latest frame is shown
            .build()
        val executor: Executor = Executors.newSingleThreadExecutor()
        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            try {
                Thread.sleep(0) //Camera preview refreshed every 10 millisec(adjust as required)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            var image: InputImage? = null
            @SuppressLint("UnsafeExperimentalUsageError") val mediaImage// Camera Feed-->Analyzer-->ImageProxy-->mediaImage-->InputImage(needed for ML kit face detection)
                    = imageProxy.image
            if (mediaImage != null) {
                image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                //                    System.out.println("Rotation "+imageProxy.getImageInfo().getRotationDegrees());
            }

//                System.out.println("ANALYSIS");

            //Process acquired image to detect faces

//                System.out.println("ANALYSIS");

            //Process acquired image to detect faces
            val result = detector!!.process(
                image!!
            )
                .addOnSuccessListener { faces ->
                    if (faces.size != 0) {
                        val face = faces[0] //Get first face from detected faces
                        //                                                    System.out.println(face);

                        //mediaImage to Bitmap
                        val frame_bmp: Bitmap = toBitmap(mediaImage!!)
                        val rot = imageProxy.imageInfo.rotationDegrees

                        //Adjust orientation of Face
                        val frame_bmp1: Bitmap =
                            rotateBitmap(frame_bmp, rot, false, false)


                        //Get bounding box of face
                        val boundingBox = RectF(face.boundingBox)

                        //Crop out bounding box from whole Bitmap(image)
                        var cropped_face: Bitmap =
                            getCropBitmapByCPU(frame_bmp1, boundingBox)
//                        if (flipX) cropped_face =
//                            rotateBitmap(cropped_face, 0, flipX, false)
                        //Scale the acquired Face to 112*112 which is required input for model
                        val scaled: Bitmap = getResizedBitmap(cropped_face, 112, 112)
                        if (start) recognizeImage(scaled) //Send scaled bitmap to create face embeddings.
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
                    imageProxy.close() //v.important to acquire next frame for analysis
                }


        }


        cameraProvider.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector!!,
            imageAnalysis,
            preview
        )

    }

    var inputSize = 112 //Input size for model

    lateinit var intValues: IntArray
    var isModelQuantized = false
    lateinit var embeedings: Array<FloatArray>
    var IMAGE_MEAN = 128.0f
    var IMAGE_STD = 128.0f
    var OUTPUT_SIZE = 192
    private var registered: java.util.HashMap<String, SimilarityClassifier.Recognition> =
        java.util.HashMap<String, SimilarityClassifier.Recognition>() //saved Faces

    var start = true


    private fun addUser() {
        start = false
        val builder = AlertDialog.Builder(this@RegisterFaceActivity)
        builder.setTitle("Enter Name")

        // Set up the input

        // Set up the input
        val input = EditText(this@RegisterFaceActivity)

        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)

        // Set up the buttons

        // Set up the buttons
        builder.setPositiveButton("ADD") { dialog, which -> //Toast.makeText(context, input.getText().toString(), Toast.LENGTH_SHORT).show();

            //Create and Initialize new object with Face embeddings and Name.
            val result = SimilarityClassifier.Recognition(
                "0", "", -1f
            )
            result.setExtra(embeedings)
            registered[input.text.toString()] = result
            start = true
            insertToSP(registered, 2)
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which ->
            start = true
            dialog.cancel()
        }

        builder.show()
    }

    private fun insertToSP(
        jsonMap: java.util.HashMap<String, SimilarityClassifier.Recognition>,
        mode: Int
    ) {
        if (mode == 1) //mode: 0:save all, 1:clear all, 2:update all
            jsonMap.clear() else if (mode == 0) jsonMap.putAll(readFromSP())
        val jsonString = Gson().toJson(jsonMap)
        //        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : jsonMap.entrySet())
//        {
//            System.out.println("Entry Input "+entry.getKey()+" "+  entry.getValue().getExtra());
//        }
        val sharedPreferences = getSharedPreferences("HashMap", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("map", jsonString)
        //System.out.println("Input josn"+jsonString.toString());
        editor.apply()
        Toast.makeText(this@RegisterFaceActivity, "Recognitions Saved", Toast.LENGTH_SHORT).show()
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
            val output = Array(2) {
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
        Toast.makeText(this@RegisterFaceActivity, "Recognitions Loaded", Toast.LENGTH_SHORT).show()
        return retrievedMap
    }

    fun recognizeImage(bitmap: Bitmap) {
        binding.ivCapture.setImageBitmap(bitmap)
        val mfn = MobileFaceNet(assets)
        embeedings = mfn.getImageEmbedding(bitmap)
    }



//    fun recognizeImage(bitmap: Bitmap) {
//
//        // set Face to Preview
//        binding.ivCapture.setImageBitmap(bitmap)
//
//        //Create ByteBuffer to store normalized image
//        val imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
//        imgData.order(ByteOrder.nativeOrder())
//        intValues = IntArray(inputSize * inputSize)
//
//        //get pixel values from Bitmap to normalize
//        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
//        imgData.rewind()
//        for (i in 0 until inputSize) {
//            for (j in 0 until inputSize) {
//                val pixelValue: Int = intValues.get(i * inputSize + j)
//                if (isModelQuantized) {
//                    // Quantized model
//                    imgData.put((pixelValue shr 16 and 0xFF).toByte())
//                    imgData.put((pixelValue shr 8 and 0xFF).toByte())
//                    imgData.put((pixelValue and 0xFF).toByte())
//                } else { // Float model
//                    imgData.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
//                    imgData.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
//                    imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
//                }
//            }
//        }
//        //imgData is input to our model
//        val inputArray = arrayOf<Any>(imgData)
//        val outputMap: MutableMap<Int, Any> = HashMap()
//        embeedings =
//            Array<FloatArray>(1) { FloatArray(OUTPUT_SIZE) } //output of model will be stored in this variable
//        outputMap[0] = embeedings
//        tfLite!!.runForMultipleInputsOutputs(inputArray, outputMap) //Run model
//        var distance_local = Float.MAX_VALUE
//        val id = "0"
//        val label = "?"
//
//        //Compare new face with saved Faces.
////        if (registered.size > 0) {
////            val nearest: List<Pair<String, Float>?> =
////                findNearest(embeedings.get(0)) //Find 2 closest matching face
////            if (nearest[0] != null) {
////                val name = nearest[0]!!.first //get name and distance of closest matching face
////                // label = name;
////                distance_local = nearest[0]!!.second
////                if (developerMode) {
////                    if (distance_local < distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
////                        reco_name.setText(
////                            """
////                        Nearest: $name
////                        Dist: ${String.format("%.3f", distance_local)}
////                        2nd Nearest: ${nearest[1]!!.first}
////                        Dist: ${String.format("%.3f", nearest[1]!!.second)}
////                        """.trimIndent()
////                        ) else reco_name.setText(
////                        """
////                        Unknown
////                        Dist: ${String.format("%.3f", distance_local)}
////                        Nearest: $name
////                        Dist: ${String.format("%.3f", distance_local)}
////                        2nd Nearest: ${nearest[1]!!.first}
////                        Dist: ${String.format("%.3f", nearest[1]!!.second)}
////                        """.trimIndent()
////                    )
////
//////                    System.out.println("nearest: " + name + " - distance: " + distance_local);
////                } else {
////                    if (distance_local < distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
////                        reco_name.setText(name) else reco_name.setText("Unknown")
////                    //                    System.out.println("nearest: " + name + " - distance: " + distance_local);
////                }
////            }
////        }
//
//
////            final int numDetectionsOutput = 1;
////            final ArrayList<SimilarityClassifier.Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
////            SimilarityClassifier.Recognition rec = new SimilarityClassifier.Recognition(
////                    id,
////                    label,
////                    distance);
////
////            recognitions.add( rec );
//    }

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


    private fun rotateBitmap(
        bitmap: Bitmap, rotationDegrees: Int, flipX: Boolean, flipY: Boolean
    ): Bitmap {
        val matrix = Matrix()

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees.toFloat())

        // Mirror the image along the X or Y axis.
        matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }

    private fun YUV_420_888toNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V
        var rowStride = image.planes[0].rowStride
        assert(image.planes[0].pixelStride == 1)
        var pos = 0
        if (rowStride == width) { // likely
            yBuffer[nv21, 0, ySize]
            pos += ySize
        } else {
            var yBufferPos = -rowStride.toLong() // not an actual position
            while (pos < ySize) {
                yBufferPos += rowStride.toLong()
                yBuffer.position(yBufferPos.toInt())
                yBuffer[nv21, pos, width]
                pos += width
            }
        }
        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride
        assert(rowStride == image.planes[1].rowStride)
        assert(pixelStride == image.planes[1].pixelStride)
        if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            val savePixel = vBuffer[1]
            try {
                vBuffer.put(1, savePixel.inv() as Byte)
                if (uBuffer[0] == savePixel.inv() as Byte) {
                    vBuffer.put(1, savePixel)
                    vBuffer.position(0)
                    uBuffer.position(0)
                    vBuffer[nv21, ySize, 1]
                    uBuffer[nv21, ySize + 1, uBuffer.remaining()]
                    return nv21 // shortcut
                }
            } catch (ex: ReadOnlyBufferException) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel)
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant
        for (row in 0 until height.div(2)) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer[vuPos]
                nv21[pos++] = uBuffer[vuPos]
            }
        }
        return nv21
    }
}