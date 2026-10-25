package com.example.qrattendance

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AccountSettings : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var back: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_account_settings)

        auth = FirebaseAuth.getInstance()

        back = findViewById(R.id.back)

        back.setOnClickListener {
            val sourceActivity = intent.getStringExtra("source_activity")

            when (sourceActivity) {
                "MainActivity" -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                }
                "Chairperson" -> {
                    val intent = Intent(this, Chairperson::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    finish()
                }
                else -> {
                    finish()
                }
            }
        }

        val forgotPasswordLink = findViewById<TextView>(R.id.btn_change_password)
        forgotPasswordLink.setOnClickListener {
            showForgotPasswordDialog()
        }

        val deleteAccountButton = findViewById<Button>(R.id.btn_delete_account)
        deleteAccountButton.setOnClickListener {
            showDeleteAccountDialog()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun showForgotPasswordDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_forgot_password)

        val emailInput = dialog.findViewById<EditText>(R.id.emailInput)
        val resetPasswordButton = dialog.findViewById<Button>(R.id.resetPasswordButton)

        resetPasswordButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isNotEmpty()) {
                auth.sendPasswordResetEmail(email)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(
                                this,
                                "Password reset email sent to $email",
                                Toast.LENGTH_SHORT
                            ).show()
                            dialog.dismiss() // Close the dialog after success
                        } else {
                            Toast.makeText(
                                this,
                                "Error: ${task.exception?.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show() // Show the dialog
    }

    private fun showDeleteAccountDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_delete_account)

        val passwordInput = dialog.findViewById<EditText>(R.id.passwordInput)
        val verifyPasswordButton = dialog.findViewById<Button>(R.id.verifyPasswordButton)

        verifyPasswordButton.setOnClickListener {
            val password = passwordInput.text.toString().trim()

            if (password.isNotEmpty()) {
                verifyPassword(password, dialog)
            } else {
                Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun verifyPassword(password: String, dialog: Dialog) {
        val user = auth.currentUser
        val email = user?.email

        if (user != null && email != null) {
            val credential = EmailAuthProvider.getCredential(email, password)

            user.reauthenticate(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // If re-authentication is successful, show confirmation dialog
                        dialog.dismiss()
                        showConfirmationDialog()
                    } else {
                        Toast.makeText(this, "Incorrect password. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Toast.makeText(this, "No user is currently logged in.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account?")
            .setPositiveButton("Yes") { _, _ ->
                deleteAccount()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun deleteAccount() {
        val user = auth.currentUser

        if (user != null) {
            val userUid = user.uid // Get the UID of the logged-in user

            // Reference to Firestore
            val firestore = FirebaseFirestore.getInstance()
            val userDocRef = firestore.collection("users").document(userUid)

            // Delete the Firestore document first
            userDocRef.delete()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // If Firestore document is deleted, proceed to delete the Firebase account
                        user.delete()
                            .addOnCompleteListener { deleteTask ->
                                if (deleteTask.isSuccessful) {
                                    Toast.makeText(
                                        this,
                                        "Account and data deleted successfully.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // Redirect to login or exit the app
                                    val intent = Intent(applicationContext, Login::class.java)
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(
                                        this,
                                        "Failed to delete account: ${deleteTask.exception?.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    } else {
                        Toast.makeText(
                            this,
                            "Failed to delete user data: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        } else {
            Toast.makeText(this, "No user is currently logged in.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        val sourceActivity = intent.getStringExtra("source_activity")

        when (sourceActivity) {
            "MainActivity" -> {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                finish()
            }
            "Chairperson" -> {
                val intent = Intent(this, Chairperson::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                finish()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }
}