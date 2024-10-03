package com.example.treadmillsdk

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
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

    // UUIDs para o serviço e características do FTMS
    private val FITNESS_MACHINE_SERVICE_UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val FITNESS_MACHINE_CONTROL_POINT_UUID = UUID.fromString("00002AD9-0000-1000-8000-00805f9b34fb")
    private val TREADMILL_DATA_CHAR_UUID = UUID.fromString("00002acd-0000-1000-8000-00805f9b34fb")

    // Variáveis de controle
    private var currentSpeed: Float = 5.0f // Velocidade inicial em Km/h
    private var currentInclination: Float = 0.0f // Inclinação inicial em %

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Verificar e solicitar permissões Bluetooth
        checkBluetoothPermissions()

        // Configuração dos botões
        val btnSpeed = findViewById<Button>(R.id.btn_speed)
        val btnInclination = findViewById<Button>(R.id.btn_inclination)

        btnSpeed.setOnClickListener {
            currentSpeed += 0.5f // Aumenta a velocidade em 0.5 Km/h
            setTreadmillSpeed(currentSpeed)
            Toast.makeText(this, "Velocidade ajustada para $currentSpeed Km/h", Toast.LENGTH_SHORT).show()
        }

        btnInclination.setOnClickListener {
            currentInclination += 1.0f // Aumenta a inclinação em 1%
            setTreadmillInclination(currentInclination)
            Toast.makeText(this, "Inclinação ajustada para $currentInclination%", Toast.LENGTH_SHORT).show()
        }
    }

    // Verificar e solicitar permissões
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
            // Solicitar permissões ao usuário
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), PERMISSIONS_REQUEST_CODE)
        } else {
            // Permissões já concedidas, iniciar configuração do Bluetooth
            setupBluetooth()
        }
    }

    // Configuração do Bluetooth após as permissões serem concedidas
    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e(TAG, "Bluetooth não está habilitado.")
            Toast.makeText(this, "Bluetooth não está habilitado", Toast.LENGTH_SHORT).show()
            return
        }

        // Iniciar escaneamento de dispositivos BLE
        startScanningForDevices()
    }

    // Iniciar escaneamento de dispositivos BLE
// Iniciar escaneamento de dispositivos BLE
    private fun startScanningForDevices() {
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
            Toast.makeText(this, "Erro de permissão Bluetooth. Verifique suas configurações.", Toast.LENGTH_SHORT).show()
        }
    }


    // Callback chamado quando um dispositivo BLE é descoberto
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                val device = it.device
                Log.d(TAG, "Dispositivo encontrado: ${device.name} - Endereço: ${device.address}")

                // Verificar se o dispositivo é a esteira alvo
                if (device.name == "FS-34EAB5") {
                    Log.d(TAG, "Dispositivo alvo encontrado: ${device.name}")

                    // Parar o escaneamento
                    startScanningForDevices()

                    // Conectar ao dispositivo
                    connectToDevice(device)
                }
            }
        }
    }

    // Método para conectar ao dispositivo
    private fun connectToDevice(device: BluetoothDevice) {
        try {
            Log.d(TAG, "Tentando conectar ao dispositivo: ${device.name} - ${device.address}")
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Erro de permissão Bluetooth ao tentar conectar: ${e.message}")
        }
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
            } else {
                Log.w(TAG, "Erro ao descobrir serviços GATT. Status: $status")
            }
        }
    }

    // Método para ajustar a velocidade da esteira
    private fun setTreadmillSpeed(speed: Float) {
        val fitnessService = bluetoothGatt?.getService(FITNESS_MACHINE_SERVICE_UUID)
        fitnessService?.let {
            val controlPointChar = it.getCharacteristic(FITNESS_MACHINE_CONTROL_POINT_UUID)
            if (controlPointChar != null) {
                val speedValue = (speed * 100).toInt()
                val command = byteArrayOf(0x03, (speedValue and 0xFF).toByte(), ((speedValue shr 8) and 0xFF).toByte())
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
                val command = byteArrayOf(0x05, (inclinationValue and 0xFF).toByte(), ((inclinationValue shr 8) and 0xFF).toByte())
                controlPointChar.value = command
                bluetoothGatt?.writeCharacteristic(controlPointChar)
                Log.d(TAG, "Comando de ajuste de inclinação enviado: $inclination%")
            }
        }
    }
}
