package com.starnest.common.ui.dialog.loading

import android.os.Bundle
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.starnest.common.R
import com.starnest.common.databinding.FragmentProgressDialogFragmentBinding
import com.starnest.common.model.Constants
import com.starnest.core.base.fragment.BaseDialogFragment
import com.starnest.core.base.viewmodel.BaseViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProgressDialogFragment :
    BaseDialogFragment<FragmentProgressDialogFragmentBinding, BaseViewModel>(BaseViewModel::class) {
    override fun layoutId() = R.layout.fragment_progress_dialog_fragment

    companion object {
        @JvmStatic
        fun newInstance(message: String) = ProgressDialogFragment().apply {
            arguments = Bundle().apply {
                putString(Constants.Intents.MESSAGE, message)
            }
        }
    }

    override fun initialize() {
        isCancelable = false
        setSize(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        binding.tvMessage.text = arguments?.getString(Constants.Intents.MESSAGE)

        Glide.with(requireContext()).load(R.drawable.ic_loading)
            .into(binding.ivLoading)
    }

}