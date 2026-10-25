package com.example.qrattendance

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.tasks.Task
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class EmailAndPassword : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var btnRegister: Button
    private lateinit var btnGoogleSignUp: LinearLayout
    private lateinit var progressBar: ProgressBar

    private var firstName: String? = null
    private var middleInitial: String? = null
    private var lastName: String? = null
    private var nameSuffix: String? = null
    private var role: String? = null
    private var department: String? = null

    companion object {
        private const val GOOGLE_SIGN_IN_REQUEST_CODE = 1001
        private const val EMAIL_DOMAIN = "@bisu.edu.ph"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_and_password)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        firstName = intent.getStringExtra("firstName")
        middleInitial = intent.getStringExtra("middleInitial")
        lastName = intent.getStringExtra("lastName")
        nameSuffix = intent.getStringExtra("nameSuffix")
        role = intent.getStringExtra("role")
        department = intent.getStringExtra("department")

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        btnRegister = findViewById(R.id.btn_register)
        btnGoogleSignUp = findViewById(R.id.btn_google_sign_up)
        progressBar = findViewById(R.id.progressBar)

        btnRegister.setOnClickListener { registerWithEmailAndPassword() }
        btnGoogleSignUp.setOnClickListener { initiateGoogleSignIn() }
    }

    private fun registerWithEmailAndPassword() {
        val email = findViewById<TextInputEditText>(R.id.email).text.toString().trim()
        val password = findViewById<TextInputEditText>(R.id.password).text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email and password fields cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!email.endsWith(EMAIL_DOMAIN)) {
            Toast.makeText(this, "Only BISU email addresses are allowed.", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters long.", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    saveUserToFirestore()
                } else {
                    handleAuthException(task.exception)
                }
            }
    }

    private fun initiateGoogleSignIn() {
        progressBar.visibility = View.VISIBLE
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, GOOGLE_SIGN_IN_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GOOGLE_SIGN_IN_REQUEST_CODE) {
            progressBar.visibility = View.VISIBLE
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.result
            if (account.email?.endsWith(EMAIL_DOMAIN) == false) {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Only BISU email addresses are allowed.", Toast.LENGTH_SHORT).show()
                return
            }

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    checkIfUserExists(account.email)
                } else {
                    progressBar.visibility = View.GONE
                    handleAuthException(authTask.exception)
                }
            }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Google Sign-In failed. Please try again.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkIfUserExists(email: String?) {
        email?.let {
            db.collection("users").whereEqualTo("email", email).get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        saveUserToFirestore()
                    } else {
                        progressBar.visibility = View.GONE
                        auth.signOut()
                        Toast.makeText(this, "This email is already registered.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error checking email existence. Try again.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun handleAuthException(exception: Exception?) {
        when (exception) {
            is FirebaseAuthUserCollisionException -> {
                Toast.makeText(this, "An account with this email already exists.", Toast.LENGTH_SHORT).show()
            }
            is FirebaseNetworkException -> {
                Toast.makeText(this, "Please check your internet connection and try again.", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "Registration error. Try again later.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserToFirestore() {
        val userId = auth.currentUser?.uid
        val userMap = hashMapOf(
            "firstName" to firstName,
            "middleInitial" to middleInitial,
            "lastName" to lastName,
            "nameSuffix" to nameSuffix,
            "role" to role,
            "department" to department,
            "email" to auth.currentUser?.email
        )

        userId?.let {
            db.collection("users").document(it)
                .set(userMap)
                .addOnSuccessListener {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "User registered successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, Login::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Failed to store data. Try again.", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
