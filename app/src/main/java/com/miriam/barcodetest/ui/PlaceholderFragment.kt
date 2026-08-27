package com.miriam.barcodetest.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.miriam.barcodetest.databinding.FragmentPlaceholderBinding

/**
 * מסך זמני לטאבים שעדיין לא נבנו. קיים כדי שסרגל הניווט יעבוד במלואו
 * כבר עכשיו - כל טאב יוחלף במסך אמיתי בשלב הייעודי לו.
 */
class PlaceholderFragment : Fragment() {

    private var _binding: FragmentPlaceholderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaceholderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments
        binding.placeholderTitle.text = args?.getString(ARG_TITLE).orEmpty()
        val iconRes = args?.getInt(ARG_ICON, 0) ?: 0
        if (iconRes != 0) {
            binding.placeholderIcon.setImageResource(iconRes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TITLE = "placeholder_title"
        private const val ARG_ICON = "placeholder_icon"

        fun newInstance(title: String, iconRes: Int): PlaceholderFragment {
            val fragment = PlaceholderFragment()
            val args = Bundle()
            args.putString(ARG_TITLE, title)
            args.putInt(ARG_ICON, iconRes)
            fragment.arguments = args
            return fragment
        }
    }
}
