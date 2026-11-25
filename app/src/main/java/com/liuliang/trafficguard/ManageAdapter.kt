package com.liuliang.trafficguard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liuliang.trafficguard.databinding.ItemManageBinding

class ManageAdapter(
    private val onToggle: (AppEntry, Boolean) -> Unit
) : RecyclerView.Adapter<ManageAdapter.Holder>() {

    private var items: List<AppEntry> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<AppEntry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].pkg.hashCode().toLong()
    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemManageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        val b = holder.binding
        b.ivIcon.setImageDrawable(app.icon)
        b.tvName.text = app.label
        b.tvPackage.text = if (app.isSystem) "系统应用 · ${app.pkg}" else app.pkg
        b.switchBlock.setOnCheckedChangeListener(null)
        b.switchBlock.isChecked = app.blocked
        b.switchBlock.setOnCheckedChangeListener { _, checked -> onToggle(app, checked) }
        b.root.setOnClickListener {
            b.switchBlock.isChecked = !b.switchBlock.isChecked
        }
    }

    class Holder(val binding: ItemManageBinding) : RecyclerView.ViewHolder(binding.root)
}
