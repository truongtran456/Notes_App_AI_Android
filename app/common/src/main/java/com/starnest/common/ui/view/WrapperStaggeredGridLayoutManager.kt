package com.starnest.common.ui.view

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

class WrapperStaggeredGridLayoutManager(
    spanCount: Int, orientation: Int
) : StaggeredGridLayoutManager(spanCount, orientation) {

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        try {
            super.onLayoutChildren(recycler, state)
        } catch (e: Exception) {
            Log.e("onLayoutChildren", e.toString())
            e.printStackTrace()
        }
    }

    override fun supportsPredictiveItemAnimations(): Boolean {
        return false
    }
}