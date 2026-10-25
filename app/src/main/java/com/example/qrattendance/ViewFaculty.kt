package com.example.qrattendance

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ViewFaculty : AppCompatActivity() {

    private lateinit var back: ImageView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_faculty)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        back = findViewById(R.id.back)

        back.setOnClickListener {
            val intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

        // Get department from intent extras
        val department = intent.getStringExtra("department")
        department?.let {
            // Display department-specific faculty label
            val facultyTextView = findViewById<TextView>(R.id.faculty)
            when (it) {
                "CCIS" -> facultyTextView.text = "CIS Faculty"
                "CTAS" -> facultyTextView.text = "CTAS Faculty"
                else -> facultyTextView.text = "CCJ Faculty"
            }

            displayDepartmentUsers(it) // Display users by department
        } ?: run {
            Toast.makeText(this, "Department not found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        val intent = Intent(applicationContext, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        finish()
        super.onBackPressed()
    }

    private fun displayDepartmentUsers(department: String) {
        val namesContainer = findViewById<LinearLayout>(R.id.namesContainer)
        namesContainer.removeAllViews()

        db.collection("users")
            .whereEqualTo("department", department)
            .get()
            .addOnSuccessListener { documents ->
                // Separate lists for roles
                val chairpersons = mutableListOf<Map<String, String>>()
                val teachingStaff = mutableListOf<Map<String, String>>()
                val nonTeachingStaff = mutableListOf<Map<String, String>>()

                for (document in documents) {
                    val name = "${document.getString("firstName")} ${document.getString("lastName")}"
                    val role = document.getString("role") ?: "Non-Teaching"

                    // Set label "Chairperson" for administrative roles and categorize by role
                    val user = mapOf("name" to name, "role" to if (role == "Administrative") "Chairperson" else role)
                    when (user["role"]) {
                        "Chairperson" -> chairpersons.add(user)
                        "Teaching" -> teachingStaff.add(user)
                        else -> nonTeachingStaff.add(user)
                    }
                }

                // Display users in role-specific order: Chairperson, Teaching, Non-Teaching
                addUsersToContainer(namesContainer, chairpersons)
                addUsersToContainer(namesContainer, teachingStaff)
                addUsersToContainer(namesContainer, nonTeachingStaff)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading users: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Helper function to add users to the container
    private fun addUsersToContainer(container: LinearLayout, users: List<Map<String, String>>) {
        for (user in users) {
            val userLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(50, 35, 40, 35)
                background = ContextCompat.getDrawable(context, R.drawable.textview_two)
            }

            // Name TextView (left-aligned)
            val nameTextView = TextView(this).apply {
                text = user["name"]
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.gray))
                textAlignment = TextView.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Role TextView (right-aligned)
            val roleTextView = TextView(this).apply {
                text = ""
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.gray))
                textAlignment = TextView.TEXT_ALIGNMENT_VIEW_END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Add TextViews to the user layout
            userLayout.addView(nameTextView)
            userLayout.addView(roleTextView)

            // Define layout parameters with margins and add to container
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 2, 0, 2)
            }
            userLayout.layoutParams = params
            container.addView(userLayout)
        }
    }
}
