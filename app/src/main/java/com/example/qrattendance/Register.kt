package com.example.qrattendance

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Register : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var btnNext: Button
    private lateinit var progressBar: ProgressBar

    override fun onBackPressed() {
        val intent = Intent(applicationContext, WelcomeActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        finish()
        super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        val middleInitialField: TextInputEditText = findViewById(R.id.middleinitial)

        // Add a TextWatcher for real-time validation
        middleInitialField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(editable: Editable?) {
                val input = editable.toString()

                // Validate and format the input
                if (input.length == 1 && input.matches("[A-Za-z]".toRegex())) {
                    middleInitialField.setText("$input.")
                    middleInitialField.setSelection(middleInitialField.text!!.length - 1) // Move cursor to end
                } else if (!input.matches("^[A-Za-z]\\.$".toRegex())) {
                    middleInitialField.error = "Enter a valid middle initial (e.g., G.)"
                }
            }
        })

        val suffixDropdown: AutoCompleteTextView = findViewById(R.id.suffix_dropdown)

        val suffixOptions = listOf("None", "Jr.", "Sr.", "II", "III", "IV")

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, suffixOptions)

        suffixDropdown.setAdapter(adapter)

        suffixDropdown.setText("None", false)

        suffixDropdown.setOnItemClickListener { parent, view, position, id ->
            val selectedSuffix = suffixOptions[position]
            // Do something with the selected suffix
            Toast.makeText(this, "Selected: $selectedSuffix", Toast.LENGTH_SHORT).show()
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        btnNext = findViewById(R.id.btn_next)
        progressBar = findViewById(R.id.progressBar)

        btnNext.setOnClickListener {
            val firstName = findViewById<TextInputEditText>(R.id.firstname).text.toString().trim()
            val middleInitial = findViewById<TextInputEditText>(R.id.middleinitial).text.toString().trim()
            val lastName = findViewById<TextInputEditText>(R.id.lastname).text.toString().trim()
            var nameSuffix = findViewById<AutoCompleteTextView>(R.id.suffix_dropdown).text.toString().trim()
            val roleId = findViewById<RadioGroup>(R.id.role).checkedRadioButtonId
            val departmentId = findViewById<RadioGroup>(R.id.department).checkedRadioButtonId

            if (nameSuffix == "None") {
                nameSuffix = "";
            }

            if (firstName.isEmpty() || lastName.isEmpty() || roleId == -1 || departmentId == -1) {
                Toast.makeText(this, "Please fill all required fields!", Toast.LENGTH_SHORT).show()
            } else {
                val role = findViewById<RadioButton>(roleId).text.toString().trim()
                val department = findViewById<RadioButton>(departmentId).text.toString().trim()

                // Show progress bar before starting Firebase operations
                progressBar.visibility = View.VISIBLE
                btnNext.isEnabled = false  // Disable button to prevent multiple clicks

                // Check for existing user and chairperson role condition
                checkUserAndRole(firstName, lastName, middleInitial, nameSuffix, role, department)
            }
        }
    }

    private fun checkUserAndRole(firstName: String, lastName: String, middleInitial: String, nameSuffix: String, role: String, department: String) {
        db.collection("users")
            .whereEqualTo("firstName", firstName)
            .whereEqualTo("lastName", lastName)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    Toast.makeText(this, "User with this name already exists!", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    btnNext.isEnabled = true  // Re-enable button
                } else {
                    if (role == "Administrative") {
                        checkChairpersonExists(department, firstName, lastName, middleInitial, nameSuffix, role)
                    } else {
                        navigateToEmailAndPassword(firstName, lastName, middleInitial, nameSuffix, role, department)
                    }
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error checking user existence: ${exception.message}", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                btnNext.isEnabled = true  // Re-enable button
            }
    }

    private fun checkChairpersonExists(department: String, firstName: String, lastName: String, middleInitial: String, nameSuffix: String, role: String) {
        db.collection("users")
            .whereEqualTo("role", "Administrative")
            .whereEqualTo("department", department)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    Toast.makeText(this, "A chairperson already exists in this department!", Toast.LENGTH_SHORT).show()
                } else {
                    navigateToEmailAndPassword(firstName, lastName, middleInitial, nameSuffix, role, department)
                }
                progressBar.visibility = View.GONE
                btnNext.isEnabled = true  // Re-enable button
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking chairperson role. Please try again.", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                btnNext.isEnabled = true  // Re-enable button
            }
    }

    private fun navigateToEmailAndPassword(firstName: String, lastName: String, middleInitial: String, nameSuffix: String, role: String, department: String) {
        val intent = Intent(this, EmailAndPassword::class.java).apply {
            putExtra("firstName", firstName)
            putExtra("middleInitial", middleInitial)
            putExtra("lastName", lastName)
            putExtra("nameSuffix", nameSuffix)
            putExtra("role", role)
            putExtra("department", department)
        }
        progressBar.visibility = View.GONE
        btnNext.isEnabled = true  // Re-enable button
        startActivity(intent)
    }
}
