package com.example.qrattendance

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {

    private lateinit var button: Button
    private lateinit var googleSignInButton: LinearLayout
    private lateinit var progressBar: ProgressBar

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val GOOGLE_SIGN_IN_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        button = findViewById(R.id.btn_login)
        googleSignInButton = findViewById(R.id.btn_google_sign_in)
        progressBar = findViewById(R.id.progressBar)

        val forgotPasswordLink = findViewById<TextView>(R.id.forgotPasswordLink)
        forgotPasswordLink.setOnClickListener {
            showForgotPasswordDialog()
        }

        button.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            loginUser()
        }

        googleSignInButton.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            initiateGoogleSignIn()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Configure Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
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

    private fun initiateGoogleSignIn() {
        // Sign out the current account to prompt account picker
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GOOGLE_SIGN_IN_REQUEST_CODE) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.result
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    checkUserRoleAndProceed()
                } else {
                    progressBar.visibility = View.GONE
                    if (authTask.exception is FirebaseNetworkException) {
                        Toast.makeText(this, "Please check your internet connection and try again.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Google Sign-In failed: ${authTask.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Google Sign-In failed. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkUserRoleAndProceed() {
        val userId = auth.currentUser?.uid

        userId?.let {
            db.collection("users").document(it).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val role = document.getString("role")
                        when (role) {
                            "Administrative" -> {
                                progressBar.visibility = View.GONE
                                startActivity(Intent(applicationContext, Chairperson::class.java))
                                finish()
                            }
                            "Teaching", "Non - Teaching" -> {
                                progressBar.visibility = View.GONE
                                startActivity(Intent(applicationContext, MainActivity::class.java))
                                finish()
                            }
                            else -> {
                                progressBar.visibility = View.GONE
                                Toast.makeText(this, "Role not defined!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Failed to retrieve user role: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun loginUser() {
        val email = findViewById<TextInputEditText>(R.id.email).text.toString().trim()
        val password = findViewById<TextInputEditText>(R.id.password).text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Please enter both email and password.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    checkUserRoleAndProceed()
                } else {
                    if (task.exception is FirebaseNetworkException) {
                        Toast.makeText(this, "Please check your internet connection and try again.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    override fun onBackPressed() {
        val intent = Intent(applicationContext, WelcomeActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        finish()
        super.onBackPressed()
    }
}
