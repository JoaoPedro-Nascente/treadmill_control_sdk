package com.example.treadmillsdk

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.util.Log
import com.example.treadmillconnectionlibrary.TreadmillConnection

class TreadmillConnection {
    private val TAG = "BluetoothLe"

    private var conn: TreadmillConnection = TreadmillConnection()

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
                    conn.stopScanningFitnessMachineDevices()

                    // Conectar ao dispositivo
                    conn.connectToDevice(device, this)
                }
            }
        }
    }
}