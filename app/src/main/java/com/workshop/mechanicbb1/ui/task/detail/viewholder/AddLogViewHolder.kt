package com.workshop.mechanicbb1.ui.task.detail.viewholder

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.workshop.mechanicbb1.R

class AddLogViewHolder(
    itemView: View,
    private val onAddLogClick: () -> Unit,
    private val onButtonClick: () -> Unit,
    private var buttonMode: String,
    private var currentStatus: String
) : RecyclerView.ViewHolder(itemView) {

    private val btn: Button = itemView.findViewById(R.id.btnAddLog)

    fun bind(newButtonMode: String, newCurrentStatus: String) {
        buttonMode = newButtonMode
        currentStatus = newCurrentStatus

        when (buttonMode) {
            "ADD_LOG" -> {
                if (currentStatus == "41" || currentStatus == "3") {
                    btn.visibility = View.GONE
                } else {
                    btn.text = "ثبت گزارش جدید"
                    btn.setOnClickListener { onAddLogClick() }
                    btn.visibility = View.VISIBLE
                    btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#9C27B0"))
                }
            }
            "FINAL_REVIEW" -> {
                btn.text = "بررسی و تأیید نهایی"
                btn.setOnClickListener { onButtonClick() }
                btn.visibility = View.VISIBLE
                btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF9800"))
            }
            else -> {
                btn.visibility = View.GONE
            }
        }
    }

    init {
        bind(buttonMode, currentStatus)
    }
}