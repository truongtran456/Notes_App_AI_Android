package com.starnest.common.model.senddata

import android.os.Parcelable
import com.starnest.domain.chat.model.AdvanceTool
import com.starnest.domain.chat.model.Attachment
import com.starnest.domain.chat.model.Group
import kotlinx.parcelize.Parcelize

@Parcelize
data class SendMessageData(
    var text: String? = null,
    var attachments: List<Attachment>? = null,
    var advanceTool: AdvanceTool? = null,
    var prefixEvent: String? = null,
    var isFromChatBottomInHome: Boolean = false,
    var isRealTimeSearchActive: Boolean = false,
    var isBeginChat: Boolean = false,
    var parentPrefixEvent: String? = null,
    var group: Group? = null,
    var isFromHistory: Boolean = false
) : Parcelable