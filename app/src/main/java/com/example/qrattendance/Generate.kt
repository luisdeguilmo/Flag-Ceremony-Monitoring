package com.example.qrattendance

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Generate : AppCompatActivity() {

    private lateinit var qrCode: ImageView
    private lateinit var back: ImageView
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_generate)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        qrCode = findViewById(R.id.qr_code)
        back = findViewById(R.id.back)

        // Get the current user
        val user = auth.currentUser
        if (user != null) {
            fetchUserDataAndGenerateQRCode(user.uid)
        } else {
            Toast.makeText(this, "User not authenticated.", Toast.LENGTH_SHORT).show()
        }

        back.setOnClickListener {
            navigateToChairpersonScreen()
        }
    }

    override fun onBackPressed() {
        navigateToChairpersonScreen()
        super.onBackPressed()
    }

    // Fetch user data from Firestore and generate QR code
    private fun fetchUserDataAndGenerateQRCode(userId: String) {
        val userDocRef = db.collection("users").document(userId)
        userDocRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val firstName = document.getString("firstName") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    val role = document.getString("role") ?: ""
                    val department = document.getString("department") ?: ""

                    val qrText = "Name: $firstName $lastName\nRole: $role\nDepartment: $department\nDate: ${getCurrentDate()}"
                    val qrCodeBitmap = generateQRCode(qrText)
                    qrCode.setImageBitmap(qrCodeBitmap)
                } else {
                    Toast.makeText(this, "User data not found!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to retrieve user data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Generate the QR code with the provided text
    private fun generateQRCode(text: String): Bitmap {
        val size = 512 // Dimensions for the QR code
        val bitMatrix: BitMatrix = try {
            MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        } catch (e: Exception) {
            Toast.makeText(this, "Error generating QR code: ${e.message}", Toast.LENGTH_SHORT).show()
            return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
                eraseColor(android.graphics.Color.WHITE) // return a blank image in case of error
            }
        }

        // Instead of looping through the BitMatrix, just create a Bitmap from the BitMatrix
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
        }
    }


    // Get the current date in a specific format
    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    // Navigate back to Chairperson screen with transition animation
    private fun navigateToChairpersonScreen() {
        val intent = Intent(applicationContext, Chairperson::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        finish()
    }
}
