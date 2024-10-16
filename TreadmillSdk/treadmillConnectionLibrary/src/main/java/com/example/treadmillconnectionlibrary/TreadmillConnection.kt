package com.example.treadmillconnectionlibrary

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import java.util.UUID
import java.util.logging.Handler

class TreadmillConnection {
    private val PERMISSIONS_REQUEST_CODE = 1
    private val TAG = "BluetoothLe"
    private var scanning = false
    private val handler = android.os.Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // 10 seconds
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null

    //UUIDs para servicos e caracteristicas fitness machine
    private val FITNESS_MACHINE_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val FITNESS_MACHINE_CONTROL_POINT_UUID = UUID.fromString("00002AD9-0000-1000-8000-00805f9b34fb")
    private val TREADMILL_DATA_CHAR_UUID = UUID.fromString("00002acd-0000-1000-8000-00805f9b34fb")

    // Variaveis de controle
    private var currentSpeed = 1.0f
    private var currentInclination = 0.0f
    private var totalDistance = 0.0f
    private var totalLaps = 0
    private var totalCalories = 0
    private var currentHeartRate = 0
    private var timeInSeconds = 0

    fun getFitnessDevices(context: Context, leScanCallback: ScanCallback){
        checkBluetoothPermissions(context, leScanCallback)
    }

    // Verificar e solicitar permissões
    private fun checkBluetoothPermissions(context: Context, leScanCallback: ScanCallback) {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        val permissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            // Solicitar permissões ao usuário
            val activity = context as? Activity
            if(activity != null) {
                ActivityCompat.requestPermissions(
                    context,
                    permissionsNeeded.toTypedArray(),
                    PERMISSIONS_REQUEST_CODE
                )
            } else {
                Log.e(TAG, "Erro: o context fornecido nao e uma activity.")
            }
        } else {
            // Permissões já concedidas, iniciar configuração do Bluetooth
            setupBluetooth(context, leScanCallback)
        }
    }

    // Configuração do Bluetooth após as permissões serem concedidas
    private fun setupBluetooth(context: Context, leScanCallback: ScanCallback) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth não está habilitado.")
            Toast.makeText(context, "Bluetooth não está habilitado", Toast.LENGTH_SHORT).show()
            return
        }

        // Iniciar escaneamento de dispositivos BLE
        startScanningForDevices(context, leScanCallback)
    }

    // Iniciar escaneamento de dispositivos BLE
    private fun startScanningForDevices(context: Context, leScanCallback: ScanCallback) {
        val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        try {
            if (!scanning) {
                // Parar o escaneamento após um período definido
                handler.postDelayed({
                    if (scanning) {
                        scanning = false
                        bluetoothLeScanner?.stopScan(leScanCallback) // Verifique se o 'leScanCallback' está sendo passado corretamente
                        Log.d(TAG, "Escaneamento finalizado.")
                    }
                }, SCAN_PERIOD)

                scanning = true
                bluetoothLeScanner?.startScan(leScanCallback) // Iniciar escaneamento
                Log.d(TAG, "Iniciando o escaneamento de dispositivos BLE.")
            } else {
                scanning = false
                bluetoothLeScanner?.stopScan(leScanCallback) // Parar escaneamento
                Log.d(TAG, "Escaneamento interrompido.")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Erro de permissão Bluetooth: ${e.message}")
            Toast.makeText(context, "Erro de permissão Bluetooth. Verifique suas configurações.", Toast.LENGTH_SHORT).show()
        }
    }

    // Método para conectar ao dispositivo
    private fun connectToDevice(device: BluetoothDevice, context: Context) {
        try {
            Log.d(TAG, "Tentando conectar ao dispositivo: ${device.name} - ${device.address}")
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Erro de permissão Bluetooth ao tentar conectar: ${e.message}")
        }
    }

    fun stopScanningFitnessMachineDevices(){

    }

    // Callback de conexão BluetoothGatt
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Conectado ao dispositivo GATT. Tentando descobrir serviços...")
                    bluetoothGatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Desconectado do dispositivo GATT.")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Serviços GATT descobertos com sucesso.")

                val fitnessService = gatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
                if(fitnessService != null) {
                    val treadmillDataChar = fitnessService.getCharacteristic(TREADMILL_DATA_CHAR_UUID)
                    if (treadmillDataChar != null) {
                        gatt.setCharacteristicNotification(treadmillDataChar, true)

                        val descriptor = treadmillDataChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)

                        Log.i(TAG, "Inscrito para receber notificações da esteira")
                    } else {
                        Log.w(TAG, "Caracteristica de dados da esteira não encontrada")
                    }
                } else {
                    Log.w(TAG, "Serviço de dados da esteira não encontrado")
                }
            } else {
                Log.w(TAG, "Erro ao descobrir serviços GATT. Status: $status")
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

    private fun processTreadmillData(data: ByteArray) {
        // Extraer los flags de los primeros 2 bytes
        val flags = data[0].toInt() or (data[1].toInt() shl 8)
        var nextPosition = 4 // La velocidad está en la posición 2 y 3, así que comenzamos en 4

        // Procesar la velocidad (siempre presente)
        val speed = (data[2].toInt() and 0xFF or ((data[3].toInt() and 0xFF) shl 8)) / 100.0f
        currentSpeed = speed
        Log.i(TAG, "Velocidad: $speed Km/h")

        // Procesar otros valores basados en los flags activados
        if ((flags and (1 shl 1)) != 0) nextPosition += 2 // Velocidad promedio
        if ((flags and (1 shl 2)) != 0) { // Distancia total
            val distanceRaw = (data[nextPosition].toInt() and 0xFF) or
                    ((data[nextPosition + 1].toInt() and 0xFF) shl 8) or
                    ((data[nextPosition + 2].toInt() and 0xFF) shl 16)

            // Convertir la distancia a metros asumiendo que está en decímetros o centésimas de metros
            val distance = distanceRaw / 1000.0f // o / 100.0f según la escala correcta
            totalDistance = distance
            Log.i(TAG, "Distancia total: $distance km")
            nextPosition += 3
        }
        if ((flags and (1 shl 3)) != 0) { // Inclinación
            val inclinationRaw = (data[nextPosition].toInt() and 0xFF) or ((data[nextPosition + 1].toInt() and 0xFF) shl 8)
            val inclination = inclinationRaw.toShort() / 10.0f // Convertir a un short para manejar números con signo y luego dividir entre 10
            currentInclination = inclination
            Log.i(TAG, "Inclinación: $inclination%")
            nextPosition += 2
        }
        if ((flags and (1 shl 7)) != 0) { // Calorías
            val laps = data[nextPosition].toInt() and 0xFF
            totalLaps = laps
            Log.i(TAG, "Numero de voltas: $laps")
            nextPosition += 2
        }
        if ((flags and (1 shl 8)) != 0) { // Frecuencia cardíaca
            val calories = data[nextPosition].toInt() and 0xFF
            totalCalories = calories
            Log.i(TAG, "Calorias: $calories kcal")
            nextPosition += 1
        }
        if (data.size > 17) {
            val heartRate = data[16].toInt() and 0xFF
            currentHeartRate = heartRate
            Log.i(TAG, "Frecuencia cardíaca: $heartRate BPM")
        }

        // Extraer el tiempo de los bytes 18 y 19 y convertir a minutos
        if (data.size > 18) {
            val timeRaw = (data[17].toInt() and 0xFF) or ((data[18].toInt() and 0xFF) shl 8)
            val timeInMinutes = timeRaw / 60 // Convertir segundos a minutos
            val remainingSeconds = timeRaw % 60 // Segundos restantes
            timeInSeconds = timeRaw
            Log.i(TAG, "Tiempo total: $timeInMinutes minutos y $remainingSeconds segundos")
        }
    }

    // Método para ajustar a velocidade da esteira
    private fun setTreadmillSpeed(speed: Float) {
        val fitnessService = bluetoothGatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
        fitnessService?.let {
            val controlPointChar = it.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT_UUID)
            if (controlPointChar != null) {
                val speedValue = (speed * 100).toInt()
                val command = byteArrayOf(0x02, (speedValue and 0xFF).toByte(), ((speedValue shr 8) and 0xFF).toByte())
                controlPointChar.value = command
                bluetoothGatt?.writeCharacteristic(controlPointChar)
                Log.d(TAG, "Comando de ajuste de velocidade enviado: $speed Km/h")
            }
        }
    }

    // Método para ajustar a inclinação da esteira
    private fun setTreadmillInclination(inclination: Float) {
        val fitnessService = bluetoothGatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
        fitnessService?.let {
            val controlPointChar = it.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT_UUID)
            if (controlPointChar != null) {
                val inclinationValue = (inclination * 10).toInt()
                val command = byteArrayOf(0x03, (inclinationValue and 0xFF).toByte(), ((inclinationValue shr 8) and 0xFF).toByte())
                controlPointChar.value = command
                bluetoothGatt?.writeCharacteristic(controlPointChar)
                Log.d(TAG, "Comando de ajuste de inclinação enviado: $inclination%")
            }
        }
    }

    // Função para iniciar a esteira
    private fun startTreadmill() {
        val fitnessService = bluetoothGatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
        fitnessService?.let {
            val controlPointChar = it.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT_UUID)
            if (controlPointChar != null) {
                // Código de operação para iniciar a esteira (0x01 é o opcode para Start/Resume)
                val command = byteArrayOf(0x07)
                controlPointChar.value = command
                bluetoothGatt?.writeCharacteristic(controlPointChar)
                Log.d(TAG, "Comando para iniciar a esteira enviado.")
            } else {
                Log.w(TAG, "Característica de controle não encontrada.")
            }
        }
    }

    // Função para parar a esteira
    private fun stopTreadmill() {
        val fitnessService = bluetoothGatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
        fitnessService?.let {
            val controlPointChar = it.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT_UUID)
            if (controlPointChar != null) {
                // Código de operação para parar a esteira (0x02 é o opcode para Stop/Pause)
                val command = byteArrayOf(0x08)
                controlPointChar.value = command
                bluetoothGatt?.writeCharacteristic(controlPointChar)
                Log.d(TAG, "Comando para parar a esteira enviado.")
            } else {
                Log.w(TAG, "Característica de controle não encontrada.")
            }
        }
    }

    fun getTreadmillSpeed(): Float {
        return currentSpeed
    }

    fun getTreadmillInclination(): Float {
        return currentInclination
    }

    fun getTreadmillActivityTime(): Int {
        return timeInSeconds
    }

    fun getTreadmillTotalCalories(): Int {
        return totalCalories
    }

    fun getTreadmillTotalDistance(): Float {
        return totalDistance
    }
}