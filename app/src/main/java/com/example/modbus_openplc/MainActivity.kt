package com.example.modbus_openplc

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager // Import GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val ipPorDefecto = "192.168.100.195"
    private val puertoModbus = 502
    private val idUnidad: Byte = 7

    private lateinit var ipAddressEditText: EditText
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var connectionStatusTextView: TextView
    private lateinit var errorTextView: TextView
    private lateinit var discreteInputsRecyclerView: RecyclerView
    private lateinit var coilsRecyclerView: RecyclerView

    private var clienteModbus: ClienteModbusTcp? = null
    private var connectionJob: Job? = null
    private var periodicReadJob: Job? = null

    private val discreteInputs = MutableList(8) { false }
    private val coils = MutableList(8) { false }

    private lateinit var discreteInputsAdapter: ModbusValueAdapter
    private lateinit var coilsAdapter: ModbusValueAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Inflate the XML layout

        // Initialize views
        ipAddressEditText = findViewById(R.id.ipAddressEditText)
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)
        errorTextView = findViewById(R.id.errorTextView)
        discreteInputsRecyclerView = findViewById(R.id.discreteInputsRecyclerView)
        coilsRecyclerView = findViewById(R.id.coilsRecyclerView)

        ipAddressEditText.setText(ipPorDefecto)

        // Setup RecyclerViews
        discreteInputsAdapter = ModbusValueAdapter(discreteInputs, isCoil = false) { _, _ -> /* No action for discrete inputs */ }
        discreteInputsRecyclerView.layoutManager = GridLayoutManager(this, 4) // Programmatically set GridLayoutManager
        discreteInputsRecyclerView.adapter = discreteInputsAdapter

        coilsAdapter = ModbusValueAdapter(coils, isCoil = true) { position, value ->
            // Handle coil write
            lifecycleScope.launch {
                errorTextView.visibility = View.GONE
                clienteModbus?.let { cliente ->
                    val solicitudEscrituraBobina = cliente.crearSolicitudEscrituraBobinaUnica(idUnidad, (position + 1).toShort(), value)
                    cliente.enviarSolicitud(solicitudEscrituraBobina).onFailure { e ->
                        errorTextView.text = "Error al escribir QX0.${position + 1}: ${e.message}"
                        errorTextView.visibility = View.VISIBLE
                        Log.e("Modbus", "Error al escribir bobina", e)
                    }
                }
            }
        }
        coilsRecyclerView.layoutManager = GridLayoutManager(this, 4) // Programmatically set GridLayoutManager
        coilsRecyclerView.adapter = coilsAdapter

        // Setup button listeners
        connectButton.setOnClickListener {
            connectDisconnectModbus()
        }

        disconnectButton.setOnClickListener {
            disconnectModbus()
        }

        updateConnectionUI(false) // Initial UI state
    }

    private fun connectDisconnectModbus() {
        if (clienteModbus?.estaConectado == true) {
            disconnectModbus()
        } else {
            connectModbus()
        }
    }

    private fun connectModbus() {
        connectionJob?.cancel()
        connectionJob = lifecycleScope.launch {
            errorTextView.visibility = View.GONE
            connectionStatusTextView.text = "Conectando..."
            updateConnectionUI(false)

            val ip = ipAddressEditText.text.toString()
            val cliente = ClienteModbusTcp(ip, puertoModbus)

            cliente.conectar().onSuccess {
                clienteModbus = cliente
                connectionStatusTextView.text = "Conectado"
                updateConnectionUI(true)
                startPeriodicReads()
            }.onFailure { e ->
                errorTextView.text = "Fallo de conexión: ${e.message}"
                errorTextView.visibility = View.VISIBLE
                connectionStatusTextView.text = "Desconectado"
                updateConnectionUI(false)
                Log.e("Modbus", "Fallo de conexión", e)
            }
        }
    }

    private fun disconnectModbus() {
        connectionJob?.cancel()
        periodicReadJob?.cancel()
        lifecycleScope.launch {
            clienteModbus?.desconectar()
            clienteModbus = null
            connectionStatusTextView.text = "Desconectado"
            updateConnectionUI(false)
        }
    }

    private fun startPeriodicReads() {
        periodicReadJob?.cancel()
        periodicReadJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000) // Actualizar cada segundo
                clienteModbus?.let { cliente ->
                    // Leer Entradas Discretas
                    cliente.enviarSolicitud(cliente.crearSolicitudLecturaEntradasDiscretas(idUnidad, 1, 8)).onSuccess { bytesRespuesta ->
                        cliente.parsearRespuestaModbus(bytesRespuesta).onSuccess { respuestaModbus ->
                            if (respuestaModbus.codigoFuncion == 0x02.toByte() && respuestaModbus.datos.isNotEmpty()) {
                                val conteoBytes = respuestaModbus.datos[0].toInt() and 0xFF
                                if (respuestaModbus.datos.size >= 1 + conteoBytes) {
                                    val valoresEntrada = respuestaModbus.datos.copyOfRange(1, 1 + conteoBytes)
                                    for (i in 0 until 8) {
                                        val indiceByte = i / 8
                                        val indiceBit = i % 8
                                        if (indiceByte < valoresEntrada.size) {
                                            discreteInputs[i] = (valoresEntrada[indiceByte].toInt() shr indiceBit and 0x01) == 0x01
                                        }
                                    }
                                    discreteInputsAdapter.notifyDataSetChanged()
                                }
                            }
                        }.onFailure { e ->
                            errorTextView.text = "Error al parsear entradas discretas: ${e.message}"
                            errorTextView.visibility = View.VISIBLE
                            Log.e("Modbus", "Error al parsear entradas discretas", e)
                        }
                    }.onFailure { e ->
                        errorTextView.text = "Error al leer entradas discretas: ${e.message}"
                        errorTextView.visibility = View.VISIBLE
                        Log.e("Modbus", "Error al leer entradas discretas", e)
                        if (e is IOException && e !is ClienteModbusTcp.ExcepcionModbus) {
                            disconnectModbus() // Assume connection lost
                        }
                    }

                    // Leer Bobinas
                    cliente.enviarSolicitud(cliente.crearSolicitudLecturaBobinas(idUnidad, 1, 8)).onSuccess { bytesRespuesta ->
                        cliente.parsearRespuestaModbus(bytesRespuesta).onSuccess { respuestaModbus ->
                            if (respuestaModbus.codigoFuncion == 0x01.toByte() && respuestaModbus.datos.isNotEmpty()) {
                                val conteoBytes = respuestaModbus.datos[0].toInt() and 0xFF
                                if (respuestaModbus.datos.size >= 1 + conteoBytes) {
                                    val valoresBobina = respuestaModbus.datos.copyOfRange(1, 1 + conteoBytes)
                                    for (i in 0 until 8) {
                                        val indiceByte = i / 8
                                        val indiceBit = i % 8
                                        if (indiceByte < valoresBobina.size) {
                                            coils[i] = (valoresBobina[indiceByte].toInt() shr indiceBit and 0x01) == 0x01
                                        }
                                    }
                                    coilsAdapter.notifyDataSetChanged()
                                }
                            }
                        }.onFailure { e ->
                            errorTextView.text = "Error al parsear bobinas: ${e.message}"
                            errorTextView.visibility = View.VISIBLE
                            Log.e("Modbus", "Error al parsear bobinas", e)
                        }
                    }.onFailure { e ->
                        errorTextView.text = "Error al leer bobinas: ${e.message}"
                        errorTextView.visibility = View.VISIBLE
                        Log.e("Modbus", "Error al leer bobinas", e)
                        if (e is IOException && e !is ClienteModbusTcp.ExcepcionModbus) {
                            disconnectModbus() // Assume connection lost
                        }
                    }
                }
            }
        }
    }

    private fun updateConnectionUI(isConnected: Boolean) {
        ipAddressEditText.isEnabled = !isConnected
        connectButton.isEnabled = !isConnected
        disconnectButton.isEnabled = isConnected

        if (isConnected) {
            connectionStatusTextView.setTextColor(Color.GREEN)
        }
        else {
            connectionStatusTextView.setTextColor(Color.RED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionJob?.cancel()
        periodicReadJob?.cancel()
        lifecycleScope.launch {
            clienteModbus?.desconectar()
        }
    }
}