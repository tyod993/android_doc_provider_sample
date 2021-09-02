package com.enfold.android_file_open_sandbox

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.contentcapture.ContentCaptureContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.enfold.android_file_open_sandbox.databinding.FragmentFirstBinding
import java.io.File

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPreferences = requireContext().getSharedPreferences(requireContext().getString(R.string.app_name), Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean(requireContext().getString(R.string.key_logged_in), true).apply()

        val directory = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        directory!!.setWritable(true)
        val file = File(directory.path.plus("/temp_file.pdf"))
        file.setWritable(true)
        file.setReadable(true)
        file.createNewFile()
        buildPDF(file)


        //TODO OPen file in new app

        binding.buttonFirst.setOnClickListener {
            val intent = Intent(Intent.ACTION_EDIT)
            intent.addCategory(Intent.CATEGORY_DEFAULT)

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intent.setDataAndType(file.toUri(), requireContext().contentResolver.getType(file.toUri()))
            val chooser = Intent.createChooser(intent, "Pick Editor")
            requireActivity().startActivityForResult(chooser, 1)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun buildPDF(file : File){
        // create a new document
        val document = PdfDocument()

        // create a page description
        val pageInfo = PdfDocument.PageInfo.Builder(100, 100, 1).create()

        // start a page
        val page = document.startPage(pageInfo)
        page.canvas.drawText("WAHOOOOOOOOOOO", 10F, 10F, Paint())

        document.finishPage(page)

        val stream = file.outputStream()
        document.writeTo(stream)
        stream.close()
        document.close()
    }
}