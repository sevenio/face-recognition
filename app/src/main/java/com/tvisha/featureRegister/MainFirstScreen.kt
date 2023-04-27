package com.tvisha.featureRegister

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tvisha.livenessdetect.MainActivity
import com.tvisha.livenessdetect.databinding.ActivityMainFirstScreenBinding
import kotlinx.coroutines.ObsoleteCoroutinesApi
import java.util.HashMap

class MainFirstScreen : AppCompatActivity(){
    private lateinit var binding : ActivityMainFirstScreenBinding

    @OptIn(ObsoleteCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainFirstScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.recognize.setOnClickListener {
            displaynameListview()
//            startActivity(Intent(this@MainFirstScreen,MainActivity::class.java))
        }
        binding.register.setOnClickListener {
            startActivity(Intent(this@MainFirstScreen, RegisterFaceActivity::class.java))
        }
    }
    var OUTPUT_SIZE = 192

    private fun readFromSP(): java.util.HashMap<String, SimilarityClassifier.Recognition> {
        val sharedPreferences = getSharedPreferences("HashMap", Context.MODE_PRIVATE)
        val defValue = Gson().toJson(java.util.HashMap<String, SimilarityClassifier.Recognition>())
        val json = sharedPreferences.getString("map", defValue)
        // System.out.println("Output json"+json.toString());
        val token: TypeToken<HashMap<String, SimilarityClassifier.Recognition>> =
            object : TypeToken<HashMap<String, SimilarityClassifier.Recognition>>() {}
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
        val builder = AlertDialog.Builder(this@MainFirstScreen)
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
                val intent = Intent(this@MainFirstScreen, MainActivity::class.java)
                intent.putExtra("selectedUser", selectedUser)
                startActivity(intent)
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

}