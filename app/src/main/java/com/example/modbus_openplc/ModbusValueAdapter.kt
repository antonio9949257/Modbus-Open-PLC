package com.example.modbus_openplc

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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

    override fun onBindViewHolder(holder: ModbusValueAdapter.ModbusValueViewHolder, position: Int) {
        val value = data[position]
        val name = if (isCoil) "QX0.${position + 1}" else "IX0.${position + 1}"
        holder.bind(name, value, isCoil, onCoilWrite, position)
    }

    override fun getItemCount(): Int = data.size

    inner class ModbusValueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val modbusValueName: TextView = itemView.findViewById(R.id.modbusValueName)
        private val modbusValueState: TextView = itemView.findViewById(R.id.modbusValueState)
        private val coilControlLayout: LinearLayout = itemView.findViewById(R.id.coilControlLayout)
        private val turnOnButton: Button = itemView.findViewById(R.id.turnOnButton)
        private val turnOffButton: Button = itemView.findViewById(R.id.turnOffButton)

        fun bind(name: String, state: Boolean, isCoil: Boolean, onCoilWrite: (position: Int, value: Boolean) -> Unit, position: Int) {
            modbusValueName.text = name
            modbusValueState.text = if (state) "ENCENDIDO" else "APAGADO"
            modbusValueState.setTextColor(if (state) Color.GREEN else Color.RED)

            if (isCoil) {
                coilControlLayout.visibility = View.VISIBLE
                turnOnButton.setOnClickListener { onCoilWrite(position, true) }
                turnOffButton.setOnClickListener { onCoilWrite(position, false) }
            } else {
                coilControlLayout.visibility = View.GONE
            }
        }
    }
}