package com.philkes.notallyx.presentation.activity.main.fragment

import android.os.Bundle
import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.data.model.Type

class ChecklistFragment : NotallyFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model.folder.value = Folder.NOTES
    }

    override fun getObservable(): LiveData<List<Item>> {
        val filtered = MediatorLiveData<List<Item>>()
        filtered.addSource(model.baseNotes!!) { notes ->
            filtered.value = notes.filter { it is com.philkes.notallyx.data.model.BaseNote && it.type == Type.LIST }
        }
        return filtered
    }

    override fun getBackground() = R.drawable.checkbox
}

