package com.enfold.android_file_open_sandbox

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file1 = File(filesDir.path + "/text1.txt")
        val file2 = File(filesDir.path + "/text2.txt")
        val file3 = File(filesDir.path + "/text3.txt")


        file1.writeText("This is file 1.")
        file2.writeText("This is file 2.")
        file3.writeText("This is file 3.")
    }
}