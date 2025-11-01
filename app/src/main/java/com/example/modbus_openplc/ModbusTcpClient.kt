package com.example.modbus_openplc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClienteModbusTcp(private val direccionIp: String, private val puerto: Int) {

    private var socket: Socket? = null
    private var flujoEntrada: InputStream? = null
    private var flujoSalida: OutputStream? = null
    private var idTransaccion: Short = 0

    val estaConectado: Boolean
        get() = socket?.isConnected == true && !socket!!.isClosed

    suspend fun conectar(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            socket = Socket()
            socket?.connect(InetSocketAddress(direccionIp, puerto), 5000) // 5 second connection timeout
            socket?.soTimeout = 5000 // Set read timeout to 5 seconds
            flujoEntrada = socket?.getInputStream()
            flujoSalida = socket?.getOutputStream()
            Result.success(Unit)
        } catch (e: IOException) {
            desconectar()
            Result.failure(e)
        }
    }

    suspend fun desconectar() = withContext(Dispatchers.IO) {
        try {
            socket?.close()
            flujoEntrada?.close()
            flujoSalida?.close()
        } catch (e: IOException) {
            e.printStackTrace() // Log error but don't rethrow
        } finally {
            socket = null
            flujoEntrada = null
            flujoSalida = null
        }
    }

    private fun obtenerSiguienteIdTransaccion(): Short {
        idTransaccion++
        if (idTransaccion == 0.toShort()) { // Ensure it doesn't stay 0 if it overflows
            idTransaccion = 1
        }
        return idTransaccion
    }

    suspend fun enviarSolicitud(solicitud: ByteArray): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (!estaConectado) {
            return@withContext Result.failure(IOException("No conectado al servidor Modbus."))
        }

        try {
            flujoSalida?.write(solicitud)
            flujoSalida?.flush()

            val bufferCabecera = ByteArray(7)
            var bytesLeidos = 0
            var totalBytesLeidos = 0

            while (totalBytesLeidos < 7) {
                bytesLeidos = flujoEntrada?.read(bufferCabecera, totalBytesLeidos, 7 - totalBytesLeidos) ?: -1
                if (bytesLeidos == -1) {
                    desconectar()
                    return@withContext Result.failure(IOException("Servidor Modbus cerró la conexión inesperadamente durante la lectura de la cabecera."))
                }
                totalBytesLeidos += bytesLeidos
            }

            val longitud = ByteBuffer.wrap(bufferCabecera, 4, 2)
                .order(ByteOrder.BIG_ENDIAN)
                .short
                .toInt()

            val longitudPduYUnidadId = longitud
            val longitudRespuestaTotal = 6 + longitudPduYUnidadId

            val bufferRespuestaCompleta = ByteArray(longitudRespuestaTotal)
            System.arraycopy(bufferCabecera, 0, bufferRespuestaCompleta, 0, 7) // Copy already read header

            totalBytesLeidos = 7 // Already read 7 bytes
            while (totalBytesLeidos < longitudRespuestaTotal) {
                bytesLeidos = flujoEntrada?.read(bufferRespuestaCompleta, totalBytesLeidos, longitudRespuestaTotal - totalBytesLeidos) ?: -1
                if (bytesLeidos == -1) {
                    desconectar()
                    return@withContext Result.failure(IOException("Servidor Modbus cerró la conexión inesperadamente durante la lectura de datos."))
                }
                totalBytesLeidos += bytesLeidos
            }

            Result.success(bufferRespuestaCompleta)

        } catch (e: IOException) {
            desconectar()
            Result.failure(e)
        }
    }

    fun crearSolicitudLecturaEntradasDiscretas(idUnidad: Byte, direccionInicio: Short, cantidad: Short): ByteArray {
        val idTransaccionActual = obtenerSiguienteIdTransaccion()
        val pdu = ByteBuffer.allocate(5)
            .order(ByteOrder.BIG_ENDIAN)
            .put(0x02) // Código de Función
            .putShort(direccionInicio)
            .putShort(cantidad)
            .array()
        return crearAduModbus(idTransaccionActual, idUnidad, pdu)
    }

    fun crearSolicitudLecturaBobinas(idUnidad: Byte, direccionInicio: Short, cantidad: Short): ByteArray {
        val idTransaccionActual = obtenerSiguienteIdTransaccion()
        val pdu = ByteBuffer.allocate(5)
            .order(ByteOrder.BIG_ENDIAN)
            .put(0x01) // Código de Función
            .putShort(direccionInicio)
            .putShort(cantidad)
            .array()
        return crearAduModbus(idTransaccionActual, idUnidad, pdu)
    }

    fun crearSolicitudEscrituraBobinaUnica(idUnidad: Byte, direccion: Short, valor: Boolean): ByteArray {
        val idTransaccionActual = obtenerSiguienteIdTransaccion()
        val valorBobina: Short = if (valor) 0xFF00.toShort() else 0x0000.toShort()
        val pdu = ByteBuffer.allocate(5)
            .order(ByteOrder.BIG_ENDIAN)
            .put(0x05) // Código de Función
            .putShort(direccion)
            .putShort(valorBobina)
            .array()
        return crearAduModbus(idTransaccionActual, idUnidad, pdu)
    }

    private fun crearAduModbus(idTransaccion: Short, idUnidad: Byte, pdu: ByteArray): ByteArray {
        val longitud = (1 + pdu.size).toShort()

        val adu = ByteBuffer.allocate(7 + pdu.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(idTransaccion)
            .putShort(0x0000) // ID de Protocolo
            .putShort(longitud)
            .put(idUnidad)
            .put(pdu)
            .array()
        return adu
    }

    fun parsearRespuestaModbus(respuesta: ByteArray): Result<RespuestaModbus> {
        if (respuesta.size < 7) {
            return Result.failure(IOException("Longitud de respuesta Modbus inválida: ${respuesta.size}"))
        }

        val buffer = ByteBuffer.wrap(respuesta).order(ByteOrder.BIG_ENDIAN)
        val idTransaccion = buffer.short
        val idProtocolo = buffer.short
        val longitud = buffer.short
        val idUnidad = buffer.get()

        if (idProtocolo != 0.toShort()) {
            return Result.failure(IOException("ID de Protocolo Modbus inválido: $idProtocolo"))
        }
        if (longitud != (respuesta.size - 6).toShort()) {
            return Result.failure(IOException("Discrepancia de longitud Modbus. Esperado $longitud, obtenido ${respuesta.size - 6}"))
        }

        val codigoFuncion = buffer.get()
        val datos = ByteArray(buffer.remaining())
        buffer.get(datos)

        if ((codigoFuncion.toInt() and 0xFF) > 0x80) {
            val codigoExcepcion = datos[0]
            return Result.failure(ExcepcionModbus("Excepción Modbus: Código de Función ${codigoFuncion - 0x80}, Código de Excepción $codigoExcepcion"))
        }

        return Result.success(RespuestaModbus(idTransaccion, idUnidad, codigoFuncion, datos))
    }

    data class RespuestaModbus(
        val idTransaccion: Short,
        val idUnidad: Byte,
        val codigoFuncion: Byte,
        val datos: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RespuestaModbus

            if (idTransaccion != other.idTransaccion) return false
            if (idUnidad != other.idUnidad) return false
            if (codigoFuncion != other.codigoFuncion) return false
            if (!datos.contentEquals(other.datos)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = idTransaccion.hashCode()
            result = 31 * result + idUnidad.hashCode()
            result = 31 * result + codigoFuncion.hashCode()
            return result
        }
    }

    class ExcepcionModbus(message: String) : IOException(message)
}