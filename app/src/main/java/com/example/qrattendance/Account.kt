package com.example.qrattendance

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Account : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var btnDelete: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_account)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        imageView = findViewById(R.id.back)
        btnDelete = findViewById(R.id.btn_delete)

        imageView.setOnClickListener {
            finish()
        }

        val fullName = intent.getStringExtra("fullName") ?: "N/A"
        val email = intent.getStringExtra("email") ?: "N/A"

        // Display data in TextViews
        findViewById<TextView>(R.id.account_profile).text = fullName.firstOrNull()?.toString() ?: ""
        findViewById<TextView>(R.id.fullname).text = fullName
        findViewById<TextView>(R.id.email).text = email

        btnDelete.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this).apply {
            setTitle("Delete Account")
            setMessage("Are you sure you want to permanently delete your account?")
            setPositiveButton("Yes") { _, _ -> deleteAccount() }
            setNegativeButton("No", null)
        }.show()
    }

    private fun deleteAccount() {
        val currentUser = auth.currentUser

        currentUser?.let { user ->
            val userId = user.uid

            // First, delete user data from Firestore
            db.collection("users").document(userId)
                .delete()
                .addOnSuccessListener {
                    // Then, delete the user account from Firebase Authentication
                    user.delete()
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Toast.makeText(this, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                                // Redirect to Login screen
                                val intent = Intent(this, Login::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                            } else {
                                Toast.makeText(this, "Failed to delete account: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to delete user data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

}