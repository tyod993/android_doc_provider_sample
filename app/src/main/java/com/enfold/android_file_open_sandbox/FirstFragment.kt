package com.enfold.android_file_open_sandbox

import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        val docProvider = DocProvider()

        val directory = requireContext().filesDir
        directory.setWritable(true)
        val file = File(directory.path.plus("/temp_file.docx"))
        file.setWritable(true)
        file.createNewFile()
        file.writeText("This is a test of your local broadcast system")


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
}