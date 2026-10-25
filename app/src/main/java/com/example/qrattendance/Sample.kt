package com.example.qrattendance

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat

class Sample : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sample)

        supportActionBar!!.setBackgroundDrawable(ColorDrawable(Color.rgb(72, 58, 178)))

        // Create a TextView with the SpannableString as its text
        val textView = TextView(this).apply {
            val spannable = SpannableString(getString(R.string.app_name))

            // Set text color
            spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Set custom font
            val typeface = ResourcesCompat.getFont(this@Sample, R.font.poppins_regular)
            typeface?.let {
                spannable.setSpan(CustomTypefaceSpan(it), 0, spannable.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            text = spannable // Set the spannable as the text
            textSize = 23f // Adjust text size as needed
            gravity = Gravity.CENTER // Center the text vertically and horizontally
        }

        // Center the TextView within the ActionBar
        val layoutParams = ActionBar.LayoutParams(
            ActionBar.LayoutParams.WRAP_CONTENT,
            ActionBar.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Apply the custom TextView as the ActionBar title view
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(false) // Disable the default title
            customView = textView
            setDisplayShowCustomEnabled(true) // Enable the custom view
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.dot_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_account -> {
                val intent = Intent(applicationContext, Login::class.java)
                startActivity(intent)
                finish()
                true
            }
            R.id.menu_logout -> {
                // Handle Logout click, maybe show a confirmation dialog
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
