package com.example.wifi

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.*
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

class MainActivity : AppCompatActivity() {

    private var wifiList: ListView? = null
    private var wifiManager: WifiManager? = null
    private val MY_PERMISSIONS_ACCESS_COARSE_LOCATION = 1
    private val MY_PERMISSIONS_ACCESS_FINE_LOCATION = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiList = findViewById(R.id.wifiList)
        val btnScan = findViewById<Button>(R.id.scanBtn)
        val btnConnectHidden = findViewById<Button>(R.id.hiddenWifi)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (wifiManager?.isWifiEnabled == false) {
            Toast.makeText(this, "Turn on Wi-Fi...", Toast.LENGTH_LONG).show()
            wifiManager?.isWifiEnabled = true
        }

        requestCoarseLocationPermission()

        btnScan.setOnClickListener {
            if (hasCoarseLocationPermission()) {
                showWifiNetworks()
            } else {
                Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
            }
        }

        btnConnectHidden.setOnClickListener {
            showConnectToHiddenWifiDialog()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSIONS_ACCESS_FINE_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showWifiNetworks()
                } else {
                    Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestCoarseLocationPermission() {
        if (hasCoarseLocationPermission().not()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                MY_PERMISSIONS_ACCESS_COARSE_LOCATION
            )
        }
    }

    private fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showWifiNetworks() {
        if (hasFineLocationPermission()) {
            val scanResults: List<ScanResult> = wifiManager?.scanResults ?: emptyList()

            val deviceList: ArrayList<String> = ArrayList()

            for (scanResult in scanResults) {
                deviceList.add("${scanResult.SSID}")
            }

            val arrayAdapter: ArrayAdapter<*> =
                ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    deviceList.toTypedArray()
                )
            wifiList?.adapter = arrayAdapter

            wifiList?.setOnItemClickListener { _, _, position, _ ->
                val selectedNetwork = scanResults[position]
                showConnectDialog(selectedNetwork)
            }
        } else {
            requestFineLocationPermission()
        }
    }

    private fun requestFineLocationPermission() {
        if (hasFineLocationPermission().not()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_ACCESS_FINE_LOCATION
            )
        }
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showConnectDialog(selectedNetwork: ScanResult) {
        val isConnected = isWifiConnected(selectedNetwork.SSID)
        val dialogBuilder = AlertDialog.Builder(this)
        val input = EditText(this)

        if (isConnected) {
            dialogBuilder.setTitle("Disconnect from ${selectedNetwork.SSID}")
                .setPositiveButton("Disconnect") { dialog, _ ->
                    disconnectFromWifi()
                    dialog.dismiss()
                }
                .setNegativeButton("Forget") { dialog, _ ->
                    forgetWifiNetwork(selectedNetwork.SSID)
                    dialog.dismiss()
                }
        } else {
            dialogBuilder.setTitle("Connect to ${selectedNetwork.SSID}")
                .setView(input)
                .setPositiveButton("Connect") { dialog, _ ->
                    val password = input.text.toString()
                    connectToWifi(selectedNetwork, password)
                    dialog.dismiss()
                }
        }

        dialogBuilder.setNeutralButton("Details") { dialog, _ ->
            val detailsDialogBuilder = AlertDialog.Builder(this)
            val detailsView = TextView(this)
            detailsView.text = selectedNetwork.capabilities
            detailsDialogBuilder.setTitle("Capabilities")
                .setView(detailsView)
                .setPositiveButton("Cancel") { innerDialog, _ ->
                    innerDialog.dismiss()
                }
            val detailsAlert = detailsDialogBuilder.create()
            detailsAlert.show()
            dialog.dismiss()
        }

        dialogBuilder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
            input.requestFocus()
        }

        val alert = dialogBuilder.create()
        alert.show()
    }

    private fun isWifiConnected(ssid: String): Boolean {
        val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
        return wifiInfo?.ssid?.replace("\"", "") == ssid
    }

    private fun connectToWifi(selectedNetwork: ScanResult, password: String) {
        val wifiConfig = WifiConfiguration()
        val ssid = "\"${selectedNetwork.SSID}\""
        val pass = "\"$password\""

        Log.d("WifiInfo", "Cred-Normal-Net: SSID-> ${ssid}, Pass-> $pass")

        wifiConfig.SSID = ssid
        wifiConfig.preSharedKey = pass

        val networkId = wifiManager?.addNetwork(wifiConfig)

        Log.d("Net ID", "NET_ID-> ${networkId}")

        if (networkId != null) {
            wifiManager?.enableNetwork(networkId, true)
            wifiManager?.disconnect()
            wifiManager?.reconnect()
            Toast.makeText(
                this,
                "Connecting to ${selectedNetwork.SSID}...",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "Failed to connect. Please check the credentials!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun forgetWifiNetwork(ssid: String) {
        val wifiConfig = WifiConfiguration()
        wifiConfig.SSID = ssid

        val networkId = wifiManager?.addNetwork(wifiConfig)

        Log.d("Net ID", "NET_ID-> ${networkId}")

        if (networkId != -1 && networkId != null) {
            wifiManager?.removeNetwork(networkId)
            wifiManager?.saveConfiguration()
            Toast.makeText(this, "Forgotten network: $ssid", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to forget network.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun disconnectFromWifi() {
        val wifiInfo: WifiInfo? = wifiManager?.connectionInfo
        val networkId = wifiInfo?.networkId

        Log.d("Net ID", "NET_ID-> ${networkId}")

        if (networkId != -1 && networkId != null) {
            wifiManager?.disableNetwork(networkId)
            wifiManager?.disconnect()
            Toast.makeText(
                this,
                "Disconnecting from ${wifiInfo!!.ssid}...",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "Not connected to any network.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showConnectToHiddenWifiDialog() {
        val dialogBuilder = AlertDialog.Builder(this)
        val inputLayout = LinearLayout(this)
        inputLayout.orientation = LinearLayout.VERTICAL

        val inputSsid = EditText(this)
        inputSsid.hint = "SSID"
        inputLayout.addView(inputSsid)

        val inputPassword = EditText(this)
        inputPassword.hint = "Password"
        inputPassword.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        inputLayout.addView(inputPassword)

        dialogBuilder.setTitle("Connect to Hidden WiFi")
            .setView(inputLayout)
            .setPositiveButton("Connect") { dialog, _ ->
                val ssid = inputSsid.text.toString()
                val password = inputPassword.text.toString()

                if (ssid.isNotEmpty() && password.isNotEmpty()) {
                    connectToHiddenWifi(ssid, password)
                } else {
                    Toast.makeText(
                        this,
                        "SSID and Password cannot be empty.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }

        val alert = dialogBuilder.create()
        alert.show()
    }

    private fun connectToHiddenWifi(ssid: String, password: String) {
        val wifiConfig = WifiConfiguration()
        val ssid = "\"${ssid}\""
        val pass = "\"$password\""

        Log.d("WifiInfo", "Cred-Normal-Net: SSID-> ${ssid}, Pass-> $pass")

        wifiManager?.startScan()
        wifiConfig.SSID = ssid
        wifiConfig.preSharedKey = pass
        wifiConfig.hiddenSSID = true
        wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)

        val networkId = wifiManager?.addNetwork(wifiConfig)

        Log.d("Net ID", "NET_ID-> ${networkId}")

        if (networkId != null) {
            wifiManager?.enableNetwork(networkId, true)
            wifiManager?.disconnect()
            wifiManager?.reconnect()
            Toast.makeText(
                this,
                "Connecting to ${ssid}...",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                this,
                "Failed to connect. Please check the credentials!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

}