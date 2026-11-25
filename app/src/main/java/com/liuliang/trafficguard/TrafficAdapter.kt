package com.liuliang.trafficguard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.liuliang.trafficguard.databinding.ItemAppBinding

class TrafficAdapter(
    private val onClick: (AppEntry) -> Unit
) : RecyclerView.Adapter<TrafficAdapter.Holder>() {

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
        Holder(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        val b = holder.binding
        b.ivIcon.setImageDrawable(app.icon)
        b.tvName.text = app.label
        b.tvPackage.text = if (app.isSystem) "系统应用 · ${app.pkg}" else app.pkg
        b.ivBlocked.visibility = if (app.blocked) View.VISIBLE else View.GONE
        b.tvUp.text = Format.speed(app.txSpeed)
        b.tvDown.text = Format.speed(app.rxSpeed)
        b.root.setOnClickListener { onClick(app) }
    }

    class Holder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)
}
