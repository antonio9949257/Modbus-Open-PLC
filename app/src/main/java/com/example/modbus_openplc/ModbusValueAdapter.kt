package com.example.modbus_openplc

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class ModbusValueAdapter(
    private val data: MutableList<Boolean>,
    private val isCoil: Boolean,
    private val onCoilWrite: (position: Int, value: Boolean) -> Unit
) : RecyclerView.Adapter<ModbusValueAdapter.ModbusValueViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModbusValueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_modbus_value, parent, false)
        return ModbusValueViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModbusValueViewHolder, position: Int) {
        val value = data[position]
        val name = if (isCoil) "QX0.${position + 1}" else "IX0.${position + 1}"
        holder.bind(name, value, isCoil, onCoilWrite, position)
    }

    override fun getItemCount(): Int = data.size

    inner class ModbusValueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val modbusValueName: TextView = itemView.findViewById(R.id.modbusValueName)
        private val statusButtonContainer: FrameLayout = itemView.findViewById(R.id.statusButtonContainer)

        fun bind(name: String, state: Boolean, isCoil: Boolean, onCoilWrite: (position: Int, value: Boolean) -> Unit, position: Int) {
            modbusValueName.text = name

            val indicatorDrawable = statusButtonContainer.background as GradientDrawable
            val color = if (state) ContextCompat.getColor(itemView.context, R.color.teslaOnColor) else ContextCompat.getColor(itemView.context, R.color.teslaError)
            indicatorDrawable.setColor(color)

            if (isCoil) {
                statusButtonContainer.setOnClickListener {
                    // Toggle the state and trigger write
                    val newState = !state
                    onCoilWrite(position, newState)
                    // UI will be updated by the periodic read, so no need to update here immediately
                }
            } else {
                // Discrete inputs are read-only, so no click listener
                statusButtonContainer.setOnClickListener(null)
                statusButtonContainer.isClickable = false
            }
        }
    }
}