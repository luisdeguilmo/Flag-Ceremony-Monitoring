package com.example.qrattendance

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import android.media.AudioAttributes
import android.media.SoundPool
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanActivity : CaptureActivity() {

    private lateinit var barcodeScannerView: DecoratedBarcodeView
    private lateinit var back: ImageView
    private lateinit var soundPool: SoundPool
    private var shutterSoundId: Int = 0
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var firstName: String? = null
    private var lastName: String? = null

    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.custom_capture_activity)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        shutterSoundId = soundPool.load(this, R.raw.beep, 1)

        barcodeScannerView.initializeFromIntent(intent)

        barcodeScannerView.decodeContinuous { result ->
            if (isProcessing) return@decodeContinuous
            isProcessing = true
            playShutterSound()

            val qrContent = result.text.split("\n")
            if (qrContent.size == 4) {
                // Split each line on ":" and handle any potential formatting issues
                val name = qrContent[0].split(":").getOrNull(1)?.trim() ?: ""
                val role = qrContent[1].split(":").getOrNull(1)?.trim() ?: ""
                val department = qrContent[2].split(":").getOrNull(1)?.trim() ?: ""
                val date = qrContent[3].split(":").getOrNull(1)?.trim() ?: ""

                val nameParts = name.split(" ") // Split by space
                val firstName = nameParts.getOrNull(0)?.trim() ?: "" // First word as first name
                val lastName = nameParts.drop(1).joinToString(" ").trim()

                // Log the parsed values for debugging
                Log.d("ParsedQRCode", "Name: $name, Role: $role, Department: $department, Date: $date")


                // Check if all fields are non-empty (this could also be more specific if needed)
                if (validateQRCode(name, role, department, date)) {
                    // Process attendance if the QR code is valid
                    processAttendance(department, firstName, lastName)
                    finish()
                } else {
                    // Provide a more descriptive failure message
                    Toast.makeText(this, "Failed to scan: Missing or invalid data in the QR code", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                // If the QR code does not have the correct number of fields (4 lines)
                Toast.makeText(this, "Failed to scan: QR code does not have the expected structure", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        back = findViewById(R.id.back)
        back.setOnClickListener { finish() }
    }

    private fun validateQRCode(name: String, role: String, department: String, date: String): Boolean {
        return name.isNotEmpty() && role.isNotEmpty() && department.isNotEmpty() && date.isNotEmpty()
    }

    private fun processAttendance(department: String, firstName: String, lastName: String) {
        val user = auth.currentUser
        if (user != null) {
            val userDocRef = db.collection("users").document(user.uid)
            userDocRef.get().addOnSuccessListener { document ->
                if (document.exists()) {
                    val currentDepartment = document.getString("department") ?: ""
                    if (department == currentDepartment) {
                        storeAttendance(department)
                        storeChairpersonAttendance(firstName, lastName)
                    } else {
                        showToast("Department mismatch.")
                        resetProcessingFlag()
                    }
                } else {
                    showToast("User data not found.")
                    resetProcessingFlag()
                }
            }.addOnFailureListener { e ->
                showToast("Failed to retrieve user data: ${e.message}")
                resetProcessingFlag()
            }
        }
    }

    private fun storeChairpersonAttendance(firstName: String, lastName: String) {
        val db = FirebaseFirestore.getInstance()
        var uid = ""

        db.collection("users")
            .whereEqualTo("firstName", firstName)
            .whereEqualTo("lastName", lastName)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    for (document in documents) {
                        uid = document.id // Document ID is typically the UID
                        Log.d("UserUID", "Found UID: $uid")

                        // Proceed to store attendance after fetching UID
                        storeAttendanceForUID(uid)
                    }
                } else {
                    Log.d("UserUID", "No user found with the given name.")
                    showToast("No user found with the given name.")
                }
            }
            .addOnFailureListener { e ->
                Log.e("UserUID", "Error fetching UID: ${e.message}")
                showToast("Error fetching UID: ${e.message}")
            }
    }

    private fun storeAttendanceForUID(uid: String) {
        val user = auth.currentUser
        if (user != null) {
            val timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) // Formats to "08:45 AM"
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            // val currentDate = "2024-11-11"
            // Data to store for each attendance record
            val attendanceData = hashMapOf(
                "status" to "P",
                "timestamp" to timestamp
            )

            // Reference to Attendance collection -> Date document -> User ID document
            val attendanceDocRef = FirebaseFirestore.getInstance()
                .collection("attendance")
                .document(currentDate)
                .collection("users")
                .document(uid)

            attendanceDocRef.set(attendanceData, SetOptions.merge()) // Merges data if document already exists
                .addOnSuccessListener {
                    showToast("Attendance recorded.")
                    Log.d("Attendance", "Attendance recorded for UID: $uid")
                    resetProcessingFlag()
                }
                .addOnFailureListener { e ->
                    showToast("Failed to store attendance: ${e.message}")
                    Log.e("Attendance", "Error storing attendance: ${e.message}")
                    resetProcessingFlag()
                }
        } else {
            showToast("User not authenticated.")
        }
    }


    private fun storeAttendance(department: String) {
        val user = auth.currentUser
        if (user != null) {
            val userId = user.uid
            val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            // val currentDate = "2024-11-11"
            val timestamp = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) // Formats to "08:45 AM"
            // Data to store for each attendance record
            val attendanceData = hashMapOf(
                "status" to "P",
                "timestamp" to timestamp
            )

            // Reference to Attendance collection -> Date document -> User ID document
            val attendanceDocRef = db.collection("attendance").document(currentDate).collection("users").document(userId)

            attendanceDocRef.set(attendanceData)
                .addOnSuccessListener {
                    showToast("Attendance recorded.")
                    resetProcessingFlag()
                }
                .addOnFailureListener { e ->
                    showToast("Failed to store attendance: ${e.message}")
                    resetProcessingFlag()
                }
        } else {
            showToast("User not authenticated.")
        }
    }


    private fun playShutterSound() {
        soundPool.play(shutterSoundId, 1f, 1f, 0, 0, 1f)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showInvalidQRCodeMessage() {
        showToast("Invalid QR code format.")
        resetProcessingFlag()
    }

    private fun resetProcessingFlag() {
        isProcessing = false
    }

    override fun onPause() {
        super.onPause()
        barcodeScannerView.pause()
    }

    override fun onResume() {
        super.onResume()
        barcodeScannerView.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}