package com.abughaith.batteryalarm.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.abughaith.batteryalarm.apps.AppUsageManager
import com.abughaith.batteryalarm.databinding.ItemAppBinding

class AppUsageAdapter(
    private val onForceStopClick: (String) -> Unit
) : ListAdapter<AppUsageManager.AppInfo, AppUsageAdapter.AppVH>(DIFF) {
    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppUsageManager.AppInfo>() {
            override fun areItemsTheSame(a: AppUsageManager.AppInfo, b: AppUsageManager.AppInfo) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppUsageManager.AppInfo, b: AppUsageManager.AppInfo) = a == b
        }
    }
    inner class AppVH(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppUsageManager.AppInfo, usageMgr: AppUsageManager) {
            binding.tvAppName.text = item.label
            binding.imgAppIcon.setImageDrawable(item.icon)
            val timeText = usageMgr.formatUsageTime(item.foregroundTimeMs)
            val tag = if (item.isSystemApp) "(تطبيق نظام)" else "(تطبيق مثبّت)"
            binding.tvAppUsage.text = "$tag • مدة الاستخدام: $timeText"
            binding.btnForceStop.setOnClickListener { onForceStopClick(item.packageName) }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppVH {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppVH(binding)
    }
    override fun onBindViewHolder(holder: AppVH, position: Int) {
        val item = getItem(position)
        val usageMgr = AppUsageManager.getInstance(holder.itemView.context)
        holder.bind(item, usageMgr)
    }
}
