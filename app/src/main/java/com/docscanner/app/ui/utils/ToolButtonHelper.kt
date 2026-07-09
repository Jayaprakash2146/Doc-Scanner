package com.docscanner.app.ui.utils

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.docscanner.app.R

object ToolButtonHelper {

    fun addTool(
        parent: LinearLayout,
        inflater: LayoutInflater,
        iconRes: Int,
        label: String,
        onClick: () -> Unit
    ): View {
        val view = inflater.inflate(R.layout.view_tool_button, parent, false)
        val btn = view.findViewById<ImageButton>(R.id.btnIcon)
        val txt = view.findViewById<TextView>(R.id.txtLabel)
        btn.setImageResource(iconRes)
        txt.text = label
        UiEffects.bindClick(view, onClick)
        parent.addView(view)
        return view
    }
}
