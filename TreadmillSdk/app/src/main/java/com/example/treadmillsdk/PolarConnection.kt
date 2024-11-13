package com.example.treadmillsdk

import android.content.Context
import android.os.StrictMode
import android.util.Log
import android.widget.Button
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarSensorSetting
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class PolarConnection(context: Context, influxDBConnection: InfluxDBConnection, hrViewTemp: Button) {

    companion object {
        private const val TAG = "PolarConnection"
    }

    private var deviceId = "C621D624"//"C61E8A23"
    private var deviceConnected = false

    private var autoConnectDisposable: Disposable? = null
    private var scanDevicesDisposable: Disposable? = null
    private var hrDisposable: Disposable? = null
    private var ecgDisposable: Disposable? = null

    private var influxConnection: InfluxDBConnection = influxDBConnection
    private var hrView: Button = hrViewTemp

    private val api: PolarBleApi by lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            context,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO
            )
        )
    }

    fun connectToPolar(polarId: String){
        api.setPolarFilter(false)

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        api.setApiCallback(object : PolarBleApiCallback() {

            override fun blePowerStateChanged(powered: Boolean) {
                Log.d("MyApp", "BLE: power: $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                deviceConnected = true
                deviceId = polarDeviceInfo.deviceId

                Log.d("MyApp", "CONNECTED: ${polarDeviceInfo.deviceId}")

                //toggleButton(connectButton, false, getString(R.string.disconnect, deviceId))
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("MyApp", "CONNECTING: ${polarDeviceInfo.deviceId}")
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                deviceConnected = false

                Log.d("MyApp", "DISCONNECTED: ${polarDeviceInfo.deviceId}")

                //toggleButton(connectButton, true, getString(R.string.connect, deviceId))
            }

            override fun bleSdkFeatureReady(
                identifier: String,
                feature: PolarBleApi.PolarBleSdkFeature
            ) {
                Log.d(TAG, "Polar BLE SDK feature $feature is ready")
                if(feature == PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
                    startColectingData(influxConnection)
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d("MyApp", "DIS INFO uuid: $uuid value $value")
            }

            override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
                TODO("Not yet implemented")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d("MyApp", "BATTERY LEVEL: $level")
            }
        })

        try {
            if (deviceConnected) {
                api.disconnectFromDevice(polarId)
            } else {
                api.connectToDevice(polarId)
            }
        } catch (polarInvalidArgument: PolarInvalidArgument){
            val attempt = if (deviceConnected){
                "disconnect"
            } else {
                "connect"
            }
            Log.e(TAG, "Failed to $attempt. Reason $polarInvalidArgument")
        }
    }

    fun startColectingData(dbConnection: InfluxDBConnection) {
        val isDisposedHr = hrDisposable?.isDisposed ?: true

        if (isDisposedHr) {
            //toggleButton(hrButton, false, getString(R.string.hr_off))
            hrDisposable = api.startHrStreaming(deviceId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { hrData: PolarHrData ->
                        for (sample in hrData.samples) {
                            Log.d(TAG, "HR     bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}")
                            hrView.text = "${sample.hr}BPM"
                            CoroutineScope(Dispatchers.Main).launch {
                                dbConnection.writeData("polarHr", "hr", sample.hr)
                                if(sample.rrAvailable){
                                    for(rrs in sample.rrsMs){
                                        dbConnection.writeData("polarRrs", "rrs", rrs)
                                    }
                                }
                            }
                        }
                    },
                    { error: Throwable ->
                        //toggleButton(hrButton, true, getString(R.string.hr))
                        Log.e(TAG, "HR stream failed. Reason $error")
                    },
                    { Log.d(TAG, "Stream complete") }
                )
        } else {
            hrDisposable?.dispose()
        }

        val isDisposedEcg = ecgDisposable?.isDisposed ?: true

        if(isDisposedEcg) {

            val defaultSampleRate = 130
            val defaultResolution = 14

            val ecgSettings = PolarSensorSetting(
                mapOf(
                    PolarSensorSetting.SettingType.SAMPLE_RATE to defaultSampleRate,
                    PolarSensorSetting.SettingType.RESOLUTION to defaultResolution
                )
            )
            ecgDisposable = api.startEcgStreaming(deviceId, ecgSettings)
                .subscribe(
                    { polarEcgData: PolarEcgData ->
                        for (data in polarEcgData.samples) {
                            CoroutineScope(Dispatchers.Main).launch {
                                dbConnection.writeData("polarEcg", "voltage", data.voltage)
                            }
                        }
                    },
                    { error: Throwable ->
                        //toggleButton(ecgButton, true, getString(R.string.ecg))
                        Log.e(TAG, "ECG stream failed. Reason $error")
                    },
                    { Log.d(TAG, "ECG stream complete") }
                )
        } else {
            //toggleButton(ecgButton, true, getString(R.string.ecg))
            // NOTE stops streaming if it is "running"
            ecgDisposable?.dispose()
        }
    }

    fun getDeviceConnected(): Boolean {
        return deviceConnected
    }

    fun destroy(){
        api.shutDown()
    }
}