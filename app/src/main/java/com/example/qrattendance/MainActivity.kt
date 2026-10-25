package com.example.qrattendance

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.integration.android.IntentIntegrator

class MainActivity : AppCompatActivity() {

    private lateinit var btnScan: Button
    private lateinit var viewFaculty: Button
    private lateinit var btnAccountSettings: Button
    private lateinit var btnLogout: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomContainer = findViewById<LinearLayout>(R.id.bottom_container)
        val bottomSheetBehavior = BottomSheetBehavior.from(bottomContainer)
        val arrowIcon = findViewById<ImageView>(R.id.arrow_icon)

        // Set initial state and other configurations
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED // Initial state

        // Set max height to allow stretching beyond 500dp
        val displayMetrics = resources.displayMetrics
        bottomSheetBehavior.maxHeight = displayMetrics.heightPixels

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // Toggle arrow icon based on the bottom sheet state
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        arrowIcon.setImageResource(R.drawable.arrow_down) // Change to "down" arrow when expanded
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        arrowIcon.setImageResource(R.drawable.arrow_up) // Change to "up" arrow when collapsed
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Optional: You can adjust the opacity or rotation of the arrow during sliding if desired
            }
        })

        arrowIcon.setOnClickListener {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        btnScan = findViewById(R.id.btn_scan)
        viewFaculty = findViewById(R.id.btn_view_faculty)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        btnScan.setOnClickListener {
            val integrator = IntentIntegrator(this)
            integrator.captureActivity = ScanActivity::class.java
            integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            integrator.setPrompt("")
            integrator.setCameraId(0)
            integrator.setOrientationLocked(true)
            integrator.setBeepEnabled(true)
            integrator.initiateScan()
        }

        viewFaculty.setOnClickListener {
            fetchCurrentUserDepartment { department ->
                val intent = Intent(this, ViewFaculty::class.java)
                intent.putExtra("department", department) // Pass department to ViewFaculty
                startActivity(intent)
                finish()
            }
        }

        btnAccountSettings = findViewById(R.id.btn_account_settings)

        btnAccountSettings.setOnClickListener {
            val intent = Intent(applicationContext, AccountSettings::class.java)
            intent.putExtra("source_activity", "MainActivity")
            startActivity(intent)
            finish()
        }

        displayNameAndEmail()

        btnLogout = findViewById(R.id.btn_logout)

        btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun fetchCurrentUserDepartment(onResult: (String?) -> Unit) {
        val currentUser = auth.currentUser
        currentUser?.let {
            val userId = it.uid

            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val department = document.getString("department")
                        if (!department.isNullOrEmpty()) {
                            // Start ViewFaculty with the department as an extra
                            val intent = Intent(this, ViewFaculty::class.java).apply {
                                putExtra("department", department)
                            }
                            startActivity(intent)
                        } else {
                            Toast.makeText(this, "Department not found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to retrieve user data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.dot_menu, menu)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && data != null) {
            val result = data.getStringExtra("SCAN_RESULT")
            Toast.makeText(this, "Scanned: $result", Toast.LENGTH_LONG).show()
        } else {
            // Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true) // Move the activity to the back of the task stack
        finish() // Finish the current activity to simulate termination
        super.onBackPressed()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this).apply {
            setTitle("Logout")
            setMessage("Do you really want to logout?")
            setPositiveButton("Yes") { _, _ ->
                auth.signOut()
                val loginIntent = Intent(this@MainActivity, Login::class.java)
                loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(loginIntent)
            }
            setNegativeButton("No", null)
        }.show()
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

    private fun displayNameAndEmail() {
        val currentUser = auth.currentUser
        val accountProfileTextView = findViewById<TextView>(R.id.account_profile)
        val currentUserName = findViewById<TextView>(R.id.current_user)
        val fullNameTextView = findViewById<TextView>(R.id.fullname)
        val emailTextView = findViewById<TextView>(R.id.email)

        currentUser?.let {
            val userId = it.uid
            val email = it.email // Retrieve the email directly from FirebaseAuth

            // Display the email in the email TextView
            emailTextView.text = email ?: "No email available"

            // Fetch additional data (e.g., first and last name) from Firestore
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val firstName = document.getString("firstName") ?: "User"
                        val lastName = document.getString("lastName") ?: ""
                        val fullName = "$firstName $lastName"

                        // Display the full name in the fullName TextView
                        fullNameTextView.text = fullName
                        currentUserName.text = firstName + "!"

                        // Display the first letter of the first name in uppercase in the account profile TextView
                        accountProfileTextView.text = firstName.firstOrNull()?.uppercase() ?: "U"
                    } else {
                        Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to load user data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(this, "No user is logged in", Toast.LENGTH_SHORT).show()
        }
    }
}
