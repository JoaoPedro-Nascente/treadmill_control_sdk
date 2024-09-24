package com.example.treadmillsdk

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : Activity() {
    private val TAG = "BluetoothLE"
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null
    private val SCAN_PERIOD: Long = 10000 // 10 segundos
    private val PERMISSIONS_REQUEST_CODE = 1

    // UUIDs para el servicio y característica FTMS
    private val FITNESS_MACHINE_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val TREADMILL_DATA_CHAR_UUID = UUID.fromString("00002acd-0000-1000-8000-00805f9b34fb")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar y solicitar los permisos necesarios
        checkBluetoothPermissions()
    }

    // Verificar y solicitar permisos
    private fun checkBluetoothPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            // Solicitar permisos al usuario
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            // Permisos ya concedidos, iniciar configuración del Bluetooth
            setupBluetooth()
        }
    }

    // Manejar el resultado de la solicitud de permisos
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(TAG, "Permisos concedidos. Configurando el Bluetooth.")
                setupBluetooth()
            } else {
                Toast.makeText(this, "Permisos necesarios no concedidos.", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Permisos necesarios no concedidos. Cerrando la aplicación.")
                finish() // Cerrar la aplicación si los permisos no fueron concedidos
            }
        }
    }

    // Configuración del Bluetooth después de que se concedan los permisos
    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth no está habilitado.")
            Toast.makeText(this, "Bluetooth no está habilitado", Toast.LENGTH_SHORT).show()
            return
        }

        // Iniciar el escaneo de dispositivos BLE
        startScanningForDevices()
    }

    // Iniciar el escaneo de dispositivos BLE
    private fun startScanningForDevices() {
        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        try {
            if (!scanning) {
                // Parar el escaneo después de un período definido
                handler.postDelayed({
                    scanning = false
                    bluetoothLeScanner?.stopScan(leScanCallback)
                    Log.d(TAG, "Escaneo finalizado.")
                }, SCAN_PERIOD)

                scanning = true
                bluetoothLeScanner?.startScan(leScanCallback)
                Log.d(TAG, "Iniciando el escaneo de dispositivos BLE.")
            } else {
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback)
                Log.d(TAG, "Escaneo interrumpido.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permiso de Bluetooth: ${e.message}")
            Toast.makeText(this, "Error de permiso de Bluetooth. Verifique sus configuraciones.", Toast.LENGTH_SHORT).show()
        }
    }

    // Callback llamado cuando se descubre un dispositivo BLE
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val device = it.device

                // Verificar si el permiso BLUETOOTH_CONNECT ha sido concedido
                val deviceName: String = if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        device.name ?: "Desconocido"
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Error al acceder al nombre del dispositivo: ${e.message}")
                        "Permiso denegado"
                    }
                } else {
                    // Permiso no concedido
                    "Permiso necesario"
                }

                Log.d(TAG, "Dispositivo encontrado: $deviceName - Dirección: ${device.address}")

                // Verificar si el dispositivo es la caminadora objetivo
                if (deviceName == "FS-34EAB5") {
                    Log.d(TAG, "Dispositivo objetivo encontrado: $deviceName")

                    // Parar el escaneo
                    startScanningForDevices()

                    // Verificar permisos antes de conectar
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        // Conectar al dispositivo
                        connectToDevice(device)
                    } else {
                        Toast.makeText(this@MainActivity, "Permiso BLUETOOTH_CONNECT necesario", Toast.LENGTH_SHORT).show()
                        Log.w(TAG, "Permiso BLUETOOTH_CONNECT necesario para conectar al dispositivo.")
                    }
                }
            }
        }
    }

    // Método para conectar al dispositivo
    private fun connectToDevice(device: BluetoothDevice) {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Intentando conectar al dispositivo: ${device.name} - ${device.address}")
                bluetoothGatt = device.connectGatt(this, false, gattCallback)
            } else {
                Toast.makeText(this, "Permiso BLUETOOTH_CONNECT necesario", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "Permiso BLUETOOTH_CONNECT necesario para conectar al dispositivo.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permiso de Bluetooth al intentar conectar: ${e.message}")
        }
    }

    // Callback de conexión del BluetoothGatt
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Conectado al dispositivo GATT. Intentando descubrir servicios...")
                    // Verificar si el permiso BLUETOOTH_CONNECT ha sido concedido antes de descubrir los servicios
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            bluetoothGatt?.discoverServices() // Descubrir servicios del dispositivo
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Error al intentar descubrir servicios: ${e.message}")
                        }
                    } else {
                        Log.w(TAG, "Permiso BLUETOOTH_CONNECT necesario para descubrir servicios.")
                        Toast.makeText(this@MainActivity, "Permiso necesario para descubrir servicios.", Toast.LENGTH_SHORT).show()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Desconectado del dispositivo GATT.")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Servicios GATT descubiertos con éxito.")

                // Buscar el servicio Fitness Machine
                val fitnessService = gatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
                if (fitnessService != null) {
                    // Buscar la característica de datos de la caminadora
                    val treadmillDataChar = fitnessService.getCharacteristic(TREADMILL_DATA_CHAR_UUID)
                    if (treadmillDataChar != null) {
                        // Suscribirse a las notificaciones de la característica de datos de la caminadora
                        gatt.setCharacteristicNotification(treadmillDataChar, true)

                        // Configurar los descriptores para habilitar notificaciones
                        val descriptor = treadmillDataChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)

                        Log.i(TAG, "Suscrito a las notificaciones de la característica de datos de la caminadora.")
                    } else {
                        Log.w(TAG, "Característica de datos de la caminadora no encontrada.")
                    }
                } else {
                    Log.w(TAG, "Servicio Fitness Machine no encontrado.")
                }
            } else {
                Log.w(TAG, "Error al descubrir servicios GATT. Status: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.let {
                if (it.uuid == TREADMILL_DATA_CHAR_UUID) {
                    // Extraer los datos de la caminadora
                    val treadmillData = it.value

                    // Registrar los datos brutos en hexadecimal para análisis
                    val hexData = treadmillData.joinToString(" ") { byte -> String.format("%02X", byte) }
                    Log.i(TAG, "Datos brutos de la caminadora: $hexData")

                    // Decodificar los datos recibidos
                    processTreadmillData(treadmillData)
                }
            }
        }
    }

    // Método para procesar y mostrar múltiples métricas de la caminadora
    private fun processTreadmillData(data: ByteArray) {
        // Extraer los flags de los primeros 2 bytes
        val flags = data[0].toInt() or (data[1].toInt() shl 8)
        var nextPosition = 4 // La velocidad está en la posición 2 y 3, así que comenzamos en 4

        // Procesar la velocidad (siempre presente)
        val speed = (data[2].toInt() and 0xFF or ((data[3].toInt() and 0xFF) shl 8)) / 100.0f
        Log.i(TAG, "Velocidad: $speed Km/h")

        // Procesar otros valores basados en los flags activados
        if ((flags and (1 shl 1)) != 0) nextPosition += 2 // Velocidad promedio
        if ((flags and (1 shl 2)) != 0) { // Distancia total
            val distanceRaw = (data[nextPosition].toInt() and 0xFF) or
                    ((data[nextPosition + 1].toInt() and 0xFF) shl 8) or
                    ((data[nextPosition + 2].toInt() and 0xFF) shl 16)

            // Convertir la distancia a metros asumiendo que está en decímetros o centésimas de metros
            val distance = distanceRaw / 1000.0f // o / 100.0f según la escala correcta
            Log.i(TAG, "Distancia total: $distance km")
            nextPosition += 3
        }
        if ((flags and (1 shl 3)) != 0) { // Inclinación
            val inclinationRaw = (data[nextPosition].toInt() and 0xFF) or ((data[nextPosition + 1].toInt() and 0xFF) shl 8)
            val inclination = inclinationRaw.toShort() / 10.0f // Convertir a un short para manejar números con signo y luego dividir entre 10
            Log.i(TAG, "Inclinación: $inclination%")
            nextPosition += 2
        }
        if ((flags and (1 shl 7)) != 0) { // Calorías
            val calories = data[nextPosition].toInt() and 0xFF
            Log.i(TAG, "Calorías quemadas: $calories")
            nextPosition += 2
        }
        if ((flags and (1 shl 8)) != 0) { // Frecuencia cardíaca
            val heartRate = data[nextPosition].toInt() and 0xFF
            Log.i(TAG, "Calorias: $heartRate kcal")
            nextPosition += 1
        }
        if (data.size > 17) {
            val heartRate = data[16].toInt() and 0xFF
            Log.i(TAG, "Frecuencia cardíaca: $heartRate BPM")
        }

        // Extraer el tiempo de los bytes 18 y 19 y convertir a minutos
        if (data.size > 18) {
            val timeRaw = (data[17].toInt() and 0xFF) or ((data[18].toInt() and 0xFF) shl 8)
            val timeInMinutes = timeRaw / 60 // Convertir segundos a minutos
            val remainingSeconds = timeRaw % 60 // Segundos restantes
            Log.i(TAG, "Tiempo total: $timeInMinutes minutos y $remainingSeconds segundos")
        }
    }

    // Método para cerrar la conexión cuando la Activity es destruida
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Cerrando la conexión GATT.")
                bluetoothGatt?.close() // Liberar el recurso BluetoothGatt cuando ya no esté en uso
                bluetoothGatt = null
            } else {
                Log.w(TAG, "Permiso BLUETOOTH_CONNECT necesario para cerrar la conexión.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error de permiso de Bluetooth al intentar cerrar la conexión: ${e.message}")
        }
    }
}
