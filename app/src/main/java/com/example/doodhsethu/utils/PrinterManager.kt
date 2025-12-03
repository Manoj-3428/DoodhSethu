package com.example.doodhsethu.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * Printer Manager for handling physical printer connections
 * Supports Bluetooth thermal printers commonly used for receipts
 */
class PrinterManager(private val context: Context) {
    
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    
    companion object {
        private const val TAG = "PrinterManager"
        // Common Bluetooth printer UUIDs
        private val PRINTER_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
    
    init {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    }
    
    /**
     * Check if Bluetooth is available and enabled
     */
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Check if Bluetooth permissions are granted
     */
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) - Use new Bluetooth permissions
            ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 11 and below - Use legacy Bluetooth permissions
            ContextCompat.checkSelfPermission(
                context, 
                android.Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Get list of paired Bluetooth devices
     */
    fun getPairedDevices(): Set<BluetoothDevice> {
        return try {
            if (hasBluetoothPermissions()) {
                bluetoothAdapter?.bondedDevices ?: emptySet()
            } else {
                Log.w(TAG, "Bluetooth permissions not granted")
                emptySet()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception accessing paired devices: ${e.message}")
            emptySet()
        }
    }
    
    /**
     * Connect to a specific Bluetooth printer
     */
    fun connectToPrinter(deviceAddress: String): Boolean {
        return try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            device?.let {
                bluetoothSocket = it.createRfcommSocketToServiceRecord(PRINTER_UUID)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                Log.d(TAG, "Connected to printer: ${device.name}")
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to printer: ${e.message}")
            false
        }
    }
    
    /**
     * Print receipt content to connected printer - optimized for 2-inch thermal printers
     */
    fun printReceipt(receiptContent: String): Boolean {
        return try {
            outputStream?.let { stream ->
                // Initialize printer
                stream.write(byteArrayOf(0x1B, 0x40)) // ESC @ - Initialize printer
                
                // Select Font B (thicker/filled font) if supported
                stream.write(byteArrayOf(0x1B, 0x4D, 0x01)) // ESC M 1 - Select Font B
                
                // Set character size to normal with bold for filled letters
                stream.write(byteArrayOf(0x1B, 0x21, 0x08)) // ESC ! 0x08 - Bold/filled font
                
                // Enable emphasis mode for even thicker characters
                stream.write(byteArrayOf(0x1B, 0x45, 0x01)) // ESC E 1 - Enable emphasis (extra bold)
                
                // Set alignment to center for title
                stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1 - Center alignment
                
                // Print content with proper formatting
                val lines = receiptContent.split("\n")
                var isMilkCollectionReceipt = false
                for ((index, line) in lines.withIndex()) {
                    when {
                        // Title line - make it bold and slightly larger
                        line.contains("MILK COLLECTION RECEIPT") || line.contains("FARMER BILLING RECEIPT") || line.contains("BILLING CYCLE SUMMARY") || line.contains("PRINTER TEST") -> {
                            stream.write(byteArrayOf(0x1B, 0x21, 0x18)) // ESC ! 0x18 - Bold + Double height for title
                            stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1 - Center
                            stream.write(line.toByteArray(Charsets.UTF_8))
                            stream.write(byteArrayOf(0x0A)) // LF
                            // After title, set content style
                            // Slightly larger content ONLY for Milk Collection receipts: double height (not width)
                            isMilkCollectionReceipt = line.contains("MILK COLLECTION RECEIPT")
                            if (isMilkCollectionReceipt) {
                                // GS ! 0x10 -> height x2, width x1
                                stream.write(byteArrayOf(0x1D, 0x21, 0x10))
                            } else {
                                // Normal content size
                                stream.write(byteArrayOf(0x1D, 0x21, 0x00))
                            }
                            stream.write(byteArrayOf(0x1B, 0x21, 0x08)) // ESC ! 0x08 - Bold/filled for content
                            stream.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0 - Left align
                        }
                        // Separator lines - center them
                        line.startsWith("=") -> {
                            stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1 - Center
                            stream.write(line.toByteArray(Charsets.UTF_8))
                            stream.write(byteArrayOf(0x0A)) // LF
                            stream.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0 - Left align
                        }
                        // Thank you message - center it
                        line.contains("Thank you") -> {
                            stream.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1 - Center
                            stream.write(line.toByteArray(Charsets.UTF_8))
                            stream.write(byteArrayOf(0x0A)) // LF
                            stream.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0 - Left align
                        }
                        // Regular content - left aligned
                        else -> {
                            stream.write(line.toByteArray(Charsets.UTF_8))
                            stream.write(byteArrayOf(0x0A)) // LF
                        }
                    }
                }

                // Reset character size to normal after printing
                stream.write(byteArrayOf(0x1D, 0x21, 0x00))
                
                // Add some space before cutting
                stream.write(byteArrayOf(0x0A)) // LF
                stream.write(byteArrayOf(0x0A)) // LF
                
                // Cut paper (if supported by printer)
                stream.write(byteArrayOf(0x1D, 0x56, 0x00)) // GS V 0 - Full cut
                
                stream.flush()
                Log.d(TAG, "Receipt sent to 2-inch printer successfully")
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to print receipt: ${e.message}")
            false
        }
    }
    
    /**
     * Disconnect from printer
     */
    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            Log.d(TAG, "Disconnected from printer")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
    }
    
    /**
     * Test print functionality - optimized for 2-inch thermal printer
     */
    fun testPrint(): Boolean {
        val testContent = """
            ================================
                  PRINTER TEST
            ================================
            
            Date: ${Date()}
            Time: ${System.currentTimeMillis()}
            
            This is a test print from
            DoodhSethu App
            
            ================================
            Test completed successfully!
            ================================
        """.trimIndent()
        
        return printReceipt(testContent)
    }
}
