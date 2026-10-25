package com.example.qrattendance

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.property.TextAlignment
import com.itextpdf.layout.property.UnitValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay

class ViewReport : AppCompatActivity() {

    private val STORAGE_PERMISSION_CODE = 101

    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    private lateinit var monthSpinner: Spinner
    private lateinit var yearSpinner: Spinner

    private val year = Calendar.getInstance().get(Calendar.YEAR)
    private val month = Calendar.getInstance().get(Calendar.MONTH) + 1

    private var selectedMonth: Int = month
    private var selectedMonthName = ""
    private var selectedYear: Int = year

    private var currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    private var names: Array<String> = arrayOf()
    private lateinit var nameLists: List<String>
    private var userIds1: Array<String> = arrayOf()
    private lateinit var userIds: List<String>

    private lateinit var imageView: ImageView
    private lateinit var notes: EditText
    private lateinit var tableLayout: TableLayout

    private var tableYPosition = 380f
    private lateinit var seekBar: SeekBar
    private lateinit var indicatorTextView: TextView

    private val hideSeekBarHandler = Handler(Looper.getMainLooper())
    private lateinit var hideSeekBarRunnable: Runnable
    private var lastTouchTime: Long = 0

    private var pdfFilePath: String? = null
    private var newFilePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_report)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        tableLayout = findViewById(R.id.tableLayout)
        notes = findViewById(R.id.notes)

        val parentLayout = findViewById<ViewGroup>(android.R.id.content) // Get the root view of the activity

        parentLayout.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val rect = Rect()
                notes.getGlobalVisibleRect(rect)
                if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    notes.clearFocus() // Remove focus from EditText
                }
            }
            false
        }

        val btnAdjustTablePosition: Button = findViewById(R.id.btnAdjustTablePosition)
        indicatorTextView = findViewById(R.id.indicatorTextView)
        seekBar = findViewById(R.id.seekBar)

        hideSeekBarRunnable = Runnable {
            seekBar.visibility = View.GONE
            indicatorTextView.visibility = View.GONE
        }

        fetchTablePosition()

        btnAdjustTablePosition.setOnClickListener {
            seekBar.visibility = View.VISIBLE
            indicatorTextView.visibility = View.VISIBLE

            // Reset any existing hide action
            hideSeekBarHandler.removeCallbacks(hideSeekBarRunnable) // No more nullable issues
            startHideCountdown(seekBar, indicatorTextView)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tableYPosition = progress.toFloat()
                indicatorTextView.text = "Position: ${tableYPosition.toInt()}"

                // Save to Firestore
                fetchUserDepartmentAndSavePosition(tableYPosition)

                seekBar?.let {
                    hideSeekBarHandler.removeCallbacks(hideSeekBarRunnable)
                    startHideCountdown(it, indicatorTextView)
                }
            }


            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Reset the countdown if the user starts interacting with the SeekBar again
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastTouchTime <= 3000) {
                    // Reset the countdown (remove callbacks and re-post the runnable)
                    hideSeekBarHandler.removeCallbacks(hideSeekBarRunnable)
                    hideSeekBarHandler.postDelayed(hideSeekBarRunnable, 3000)
                }
                // Update the last touch time
                lastTouchTime = currentTime
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                hideSeekBarHandler.removeCallbacks(hideSeekBarRunnable)
                hideSeekBarHandler.postDelayed(hideSeekBarRunnable, 3000)
            }
        })


        val btnSaveAndPrint: Button = findViewById(R.id.btnSaveAndPrint)
        btnSaveAndPrint.setOnClickListener {
            Log.d("SaveAndPrint", "Save and Print button clicked")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Request permissions dynamically for Android 13+
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    STORAGE_PERMISSION_CODE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage doesn't need WRITE_EXTERNAL_STORAGE
                saveAndPrintReport()
            } else {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        STORAGE_PERMISSION_CODE
                    )
                } else {
                    saveAndPrintReport()
                }
            }
        }

        imageView = findViewById(R.id.back)

        imageView.setOnClickListener {
            val intent = Intent(applicationContext, Chairperson::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            finish()
        }

        monthSpinner = findViewById(R.id.monthSpinner)
        yearSpinner = findViewById(R.id.yearSpinner)

        val months = arrayOf("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")

        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        monthSpinner.adapter = monthAdapter

        val years = ArrayList<String>()
        for (i in 2024..2050) {
            years.add(i.toString())
        }
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, years)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        yearSpinner.adapter = yearAdapter

        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)

        monthSpinner.setSelection(currentMonth - 1)
        yearSpinner.setSelection(years.indexOf(currentYear.toString()))

        var isInitialLoad = true

        monthSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                selectedMonth = position + 1
                selectedMonthName = months[position]

                if (isInitialLoad) {
                    isInitialLoad = false
                    return
                }

                handleSpinnerSelection()
                fetchNotes()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {

            }
        }

        yearSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                selectedYear = parent.getItemAtPosition(position).toString().toInt()

                if (isInitialLoad) {
                    isInitialLoad = false
                    return
                }

                handleSpinnerSelection()
                fetchNotes()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {

            }
        }

            monthSpinner.post {
            isInitialLoad = true
        }

        fetchNotes()

        val btnAdd = findViewById<Button>(R.id.btn_add)

        btnAdd.setOnClickListener {
            storeNotes(notes.text.toString().trim())
        }
    }

    private fun updateSeekBarAndIndicator(position: Float) {
        seekBar.progress = position.toInt()
        indicatorTextView.text = "Position: ${position.toInt()}"
    }

    private fun fetchTablePosition() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            // Fetch the user's department
            db.collection("users")
                .document(userId) // Assuming user data is stored in this document
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val department = document.getString("department") // Adjust the field name
                        if (department != null) {
                            // Now fetch the tableYPosition for this department
                            db.collection("tableposition")
                                .document(department)
                                .get()
                                .addOnSuccessListener { positionDoc ->
                                    if (positionDoc != null && positionDoc.exists()) {
                                        val tableYPosition = positionDoc.getDouble("tableYPosition")?.toFloat()
                                        if (tableYPosition != null) {
                                            updateSeekBarAndIndicator(tableYPosition)
                                        } else {
                                            Log.e("Firestore", "tableYPosition not found")
                                        }
                                    } else {
                                        Log.e("Firestore", "Position document not found for department: $department")
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("Firestore", "Error fetching tableYPosition", e)
                                }
                        } else {
                            Log.e("Firestore", "Department not found for user")
                        }
                    } else {
                        Log.e("Firestore", "User document not found")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error fetching user department", e)
                }
        } else {
            Log.e("Firestore", "User not logged in")
        }
    }


    private fun fetchUserDepartmentAndSavePosition(position: Float) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            db.collection("users")
                .document(userId) // Assuming each user's data is stored in a document with their UID
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val department = document.getString("department") // Adjust field name
                        if (department != null) {
                            savePositionToFirestore(position, department)
                        } else {
                            Log.e("Firestore", "Department not found for user")
                        }
                    } else {
                        Log.e("Firestore", "User document not found")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error fetching user department", e)
                }
        } else {
            Log.e("Firestore", "User not logged in")
        }
    }

    private fun savePositionToFirestore(position: Float, department: String) {
        val positionData = hashMapOf(
            "tableYPosition" to position
        )

        db.collection("tableposition")
            .document(department) // Use the fetched department as the document ID
            .set(positionData)
            .addOnSuccessListener {
                Log.d("Firestore", "Position saved successfully for department $department")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving position", e)
            }
    }


    private fun startHideCountdown(seekBar: SeekBar, indicatorTextView: TextView) {
        hideSeekBarRunnable = Runnable {
            seekBar.visibility = View.GONE
            indicatorTextView.visibility = View.GONE
        }
        hideSeekBarHandler.postDelayed(hideSeekBarRunnable!!, 3000) // 3 seconds
    }

    private fun saveAndPrintReport() {
        val documentId = "$selectedYear-$selectedMonth"
        var dates: List<String> = listOf() // Use an immutable List initially

        db.collection("tableheaders").document(documentId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val headers = document.get("headers") as? List<String>
                    if (headers != null) {
                        // Reassign dates to the fetched headers (immutable list)
                        dates = headers


                    }
                } else {
                    dates = generateMondaysAndFridays(selectedYear, selectedMonth)
                }

                Log.d("SaveAndPrint", "Generated dates: $dates") // Log the generated dates

                fetchAttendanceData(userIds, dates) { attendanceMap ->
                    Log.d("SaveAndPrint", "Fetched attendance data: $attendanceMap") // Log the fetched attendance data

                    val pdfTable = createPdfTable(userIds, nameLists, dates, attendanceMap)
                    Log.d("SaveAndPrint", "PDF table created.") // Log that the PDF table was created

                    createPdf(pdfTable)
                    Log.d("SaveAndPrint", "PDF created at path: $newFilePath") // Log the path where the PDF is created

                    // Introduce delay using Coroutine
                    lifecycleScope.launch {
                        delay(2000) // Delay for 2 seconds (2000 ms)
                        Log.d("SaveAndPrint", "Delay complete, checking PDF file at $newFilePath") // Log after delay

                        val pdfFile = File(newFilePath.toString().trim())
                        if (pdfFile.exists() && pdfFile.length() > 0) {
                            Log.d("SaveAndPrint", "PDF file exists and is valid. Size: ${pdfFile.length()} bytes")

                            try {
                                // Use FileProvider to get the URI for sharing the file
                                val uri = FileProvider.getUriForFile(
                                    this@ViewReport,
                                    "com.example.qrattendance.fileprovider", // Ensure this matches your FileProvider path
                                    pdfFile
                                )
                                Log.d("SaveAndPrint", "File URI: $uri") // Log the URI for the file

                                // Use the absolute path for the PrintDocumentAdapter
                                val printManager = getSystemService(PRINT_SERVICE) as PrintManager
                                Log.d("SaveAndPrint", "PrintManager obtained.") // Log that the PrintManager is ready

                                val printDocumentAdapter = PdfDocumentAdapter(this@ViewReport, pdfFile.absolutePath)
                                printManager.print(
                                    "Report_Print",
                                    printDocumentAdapter,
                                    PrintAttributes.Builder().build()
                                )
                                Log.d("SaveAndPrint", "Print job started.") // Log that the print job has been triggered

                            } catch (e: Exception) {
                                Log.e("SaveAndPrint", "Error starting print job: ${e.message}", e)
                                Toast.makeText(this@ViewReport, "Printing failed.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Log.e("SaveAndPrint", "PDF file not found or invalid: $pdfFile, Size: ${pdfFile.length()}")
                            Toast.makeText(this@ViewReport, "PDF creation failed.", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }
    }

    private fun storeNotes(notes: String) {
        val user = auth.currentUser
        if (user != null) {
            val userId = user.uid
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { userDocument ->
                    if (userDocument.exists()) {
                        val department = userDocument.getString("department")
                        if (!department.isNullOrEmpty()) {
                            val currentDate = "$selectedYear-$selectedMonth"
                            val attendanceDocRef = db.collection("attendance")
                                .document(currentDate)
                                .collection("department")
                                .document(department)

                            attendanceDocRef.get()
                                .addOnSuccessListener { document ->
                                    if (document.exists()) {
                                        attendanceDocRef.update("notes", notes)
                                            .addOnSuccessListener {
                                                Toast.makeText(this, "Notes successfully updated.", Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(this, "Failed to update notes. Please try again.", Toast.LENGTH_SHORT).show()
                                            }
                                    } else {
                                        val attendanceData = hashMapOf("notes" to notes)
                                        attendanceDocRef.set(attendanceData)
                                            .addOnSuccessListener {
                                                Toast.makeText(this, "Notes successfully added.", Toast.LENGTH_SHORT).show()
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(this, "Failed to add notes. Please try again.", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Failed to check for existing notes. Please try again.", Toast.LENGTH_SHORT).show()
                                    Log.e("FirestoreDebug", "Error fetching document: ${it.message}", it)
                                }
                        } else {
                            Toast.makeText(this, "Department not found for the user.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Failed to fetch user data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    Log.e("FirestoreDebug", "Error fetching user data: ${exception.message}", exception)
                }
        } else {
            Toast.makeText(this, "User not authenticated. Please log in.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchNotes() {
        val user = auth.currentUser
        if (user != null) {
            val userId = user.uid

            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { userDocument ->
                    if (userDocument.exists()) {
                        val department = userDocument.getString("department")

                        if (!department.isNullOrEmpty()) {
                            val currentDate = "$selectedYear-$selectedMonth"
                            val attendanceDocRef = db.collection("attendance")
                                .document(currentDate)
                                .collection("department")
                                .document(department)

                            attendanceDocRef.get()
                                .addOnSuccessListener { document ->
                                    if (document.exists()) {
                                        Log.d("FirestoreDebug", "Document exists: ${document.data}")
                                        val existingNotes = document.getString("notes") ?: ""
                                        notes.setText(existingNotes)
                                    } else {
                                        Log.d("FirestoreDebug", "Document does not exist.")
                                        notes.setText("")
                                    }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(
                                        this,
                                        "Failed to fetch notes. Please try again.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    Log.e("FirestoreDebug", "Error fetching document: ${it.message}", it)
                                }
                        } else {
                            Toast.makeText(this, "Department not found for user.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "User data not found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to fetch user department.", Toast.LENGTH_SHORT).show()
                    Log.e("FirestoreDebug", "Error fetching user document: ${it.message}", it)
                }
        } else {
            Toast.makeText(this, "User not authenticated. Please log in.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createPdfTable(
        userIds: List<String>,
        names: List<String>,
        dates: List<String>,
        attendanceMap: Map<String, Map<String, String>>
    ): Table {
        val columnWidths = floatArrayOf(1f) + FloatArray(dates.size) { 1f }
        val pdfTable = Table(columnWidths)

        pdfTable.addCell(
            Cell(1, 1)
                .add(Paragraph("Faculty").setTextAlignment(TextAlignment.CENTER).setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
                    .setFontSize(10f))
        )
        for (date in dates) {
            pdfTable.addCell(
                Cell(1, 1)
                    .add(Paragraph(date).setTextAlignment(TextAlignment.CENTER).setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA)).setFontSize(10f))
            )
        }

        val addedNames = mutableSetOf<String>()
        Log.d("PdfTableCreation", "Total Names: ${names.size}, Total User IDs: ${userIds.size}")

        for ((index, name) in names.withIndex()) {
            val userId = userIds.getOrNull(index)
            if (!addedNames.contains(name.trim())) {
                Log.d("PdfTableCreation", "Adding Row: $name (Index: $index, UserId: $userId)")
                addDataToPdfTable(pdfTable, index + 1, name, dates, attendanceMap, userId)
                addedNames.add(name)
            } else {
                Log.d("DuplicateName", "Skipping duplicate name: $name")
            }
        }

        return pdfTable
    }

    private fun addDataToPdfTable(
        pdfTable: Table,
        rowNum: Int,
        name: String,
        dates: List<String>,
        attendanceMap: Map<String, Map<String, String>>,
        userId: String?
    ) {
        Log.d("AddDataToPdfTable", "Adding data for row: $rowNum, Name: $name, UserId: $userId")


        pdfTable.addCell(
            Cell(1, 1)
                .add(Paragraph("$rowNum. $name")
                    .setTextAlignment(TextAlignment.LEFT)
                    .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))
                    .setFontSize(10f)
                )
                .setPaddingLeft(5f)
        )

        for (date in dates) {
            val fullDate = "$selectedYear-$date"
            val status = attendanceMap[fullDate]?.get(userId) ?: "A"
            val bgColor = if (status == "P") com.itextpdf.kernel.colors.ColorConstants.GREEN else com.itextpdf.kernel.colors.ColorConstants.WHITE
            val text = if (status == "P") " " else " "

            Log.d("AddDataToPdfTable", "Date: $fullDate, Status: $status, Background Color: $bgColor")

            pdfTable.addCell(
                Cell(1, 1)
                    .add(Paragraph(text).setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(bgColor)
            )
        }
    }

    fun handleSpinnerSelection() {
        if (selectedMonth != -1 && selectedYear != -1) { // Ensure both are selected
            if (currentUserId != null) {
                fetchAndSortNames(currentUserId.toString()) {
                    setupTableWithAttendance(currentUserId.toString())
                }
            } else {
                Log.e("UserError", "No user is currently logged in.")
            }
        }
    }

    private fun addSubHeaderRow(tableLayout: TableLayout, dates: List<String>) {
        val subHeaderRow = TableRow(this)

        // Create and add the "Faculty" header
        subHeaderRow.addView(createTextView("Faculty", Color.LTGRAY, Gravity.CENTER))

        // Reference to Firestore
        val db = FirebaseFirestore.getInstance()
        val documentId = "$selectedYear-$selectedMonth" // Format document ID as "year-month"

        // Check Firestore for the headers
        db.collection("tableheaders").document(documentId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // If document exists, use its headers field
                    val headers = document.get("headers") as? List<String>
                    if (headers != null) {
                        for (header in headers) {
                            val headerTextView = createTextView(header, Color.LTGRAY, Gravity.CENTER)

                            // Set an OnClickListener to show the DatePickerDialog
                            headerTextView.setOnClickListener {
                                showDatePickerDialog(headerTextView)
                            }

                            subHeaderRow.addView(headerTextView)
                        }
                    }
                } else {
                    // If document doesn't exist, use the default dates
                    for (date in dates) {
                        val dateTextView = createTextView(date, Color.LTGRAY, Gravity.CENTER)

                        // Set an OnClickListener to show the DatePickerDialog
                        dateTextView.setOnClickListener {
                            showDatePickerDialog(dateTextView)
                        }

                        subHeaderRow.addView(dateTextView)
                    }
                }

                // Add the row to the table
                tableLayout.addView(subHeaderRow)
            }
            .addOnFailureListener { exception ->
                println("Error retrieving headers: $exception")

                // Fallback to default dates if Firestore retrieval fails
                for (date in dates) {
                    val dateTextView = createTextView(date, Color.LTGRAY, Gravity.CENTER)

                    // Set an OnClickListener to show the DatePickerDialog
                    dateTextView.setOnClickListener {
                        showDatePickerDialog(dateTextView)
                    }

                    subHeaderRow.addView(dateTextView)
                }

                // Add the row to the table
                tableLayout.addView(subHeaderRow)
            }
    }


    // Function to create a DatePickerDialog
    private fun showDatePickerDialog(targetTextView: TextView) {
        val calendar = Calendar.getInstance()
        calendar.set(selectedYear, selectedMonth - 1, calendar.get(Calendar.DAY_OF_MONTH))

        // Initialize the DatePickerDialog
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                // Get the previous value
                val previousDate = targetTextView.text.toString()

                // Update the TextView with the selected date, ensuring day and month are 2 digits
                val selectedDate = "${month + 1}".padStart(2, '0') + "-" + "${dayOfMonth}".padStart(2, '0') // Adjust for zero-based month
                targetTextView.text = selectedDate

                // If the value has changed, save the updated headers to Firestore
                if (selectedDate != previousDate) {
                    saveHeadersToFirestore(getCurrentHeaders(tableLayout))
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }


    private fun getCurrentHeaders(tableLayout: TableLayout): List<String> {
        val headers = mutableListOf<String>()

        // Assuming the header row is the first row in the TableLayout
        val headerRow = tableLayout.getChildAt(0) as? TableRow // Change index if needed

        if (headerRow != null) {
            for (i in 1 until headerRow.childCount) {
                val textView = headerRow.getChildAt(i) as? TextView
                if (textView != null) {
                    headers.add(textView.text.toString())
                }
            }
        }

        return headers
    }

    private fun saveHeadersToFirestore(headers: List<String>) {
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf("headers" to headers)

        val currentDate = "$selectedYear-$selectedMonth"

        db.collection("tableheaders").document(currentDate) // Use a fixed document ID to overwrite
            .set(data)
            .addOnSuccessListener {
                println("Headers successfully updated in Firestore!")
            }
            .addOnFailureListener { e ->
                println("Error saving headers to Firestore: $e")
            }
    }

    private fun fetchAndSortNames(currentUserId: String, onComplete: () -> Unit) {
        db.collection("users").document(currentUserId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val department = document.getString("department")
                    if (!department.isNullOrEmpty()) {
                        db.collection("users")
                            .whereEqualTo("department", department)
                            .get()
                            .addOnSuccessListener { querySnapshot ->
                                // Fetch names and userIds and store them together
                                val fetchedNames = querySnapshot.documents.mapNotNull { doc ->
                                    val firstName = doc.getString("firstName")
                                    val middleInitial = doc.getString("middleInitial")
                                    val lastName = doc.getString("lastName")
                                    val nameSuffix = doc.getString("nameSuffix")
                                    val userId = doc.id // Assuming the userId is the document ID

                                    if (!firstName.isNullOrEmpty() && !lastName.isNullOrEmpty() && !middleInitial.isNullOrEmpty() && !nameSuffix.isNullOrEmpty()) {
                                        "$lastName, $firstName $middleInitial, $nameSuffix" to userId
                                    } else if (!firstName.isNullOrEmpty() && !lastName.isNullOrEmpty() && !middleInitial.isNullOrEmpty() && nameSuffix.isNullOrEmpty()) {
                                        "$lastName, $firstName $middleInitial" to userId
                                    } else if (!firstName.isNullOrEmpty() && !lastName.isNullOrEmpty() && middleInitial.isNullOrEmpty() && !nameSuffix.isNullOrEmpty()) {
                                        "$lastName, $firstName $nameSuffix" to userId
                                    } else if (!firstName.isNullOrEmpty() && !lastName.isNullOrEmpty() && middleInitial.isNullOrEmpty() && nameSuffix.isNullOrEmpty()) {
                                        "$lastName, $firstName" to userId
                                    } else null
                                }

                                // Sort the names with their userIds
                                val sortedNames = fetchedNames.sortedBy { it.first } // Sort by name

                                // Store names and userIds separately in global variables
                                names = sortedNames.map { it.first }.toTypedArray()  // Store only names
                                userIds1 = sortedNames.map { it.second }.toTypedArray()  // Store corresponding userIds
                                userIds = sortedNames.map { it.second }
                                nameLists = sortedNames.map { it.first }

                                // Proceed to setup the table or other tasks
                                onComplete()
                            }
                            .addOnFailureListener { e ->
                                Log.e("FetchError", "Error fetching users: ", e)
                            }
                    } else {
                        Log.e("FetchError", "Department is null or empty")
                    }
                } else {
                    Log.e("FetchError", "Current user document does not exist")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FetchError", "Error fetching current user: ", e)
            }
    }

    private fun setupTableWithAttendance(userId: String) {

        tableLayout.removeAllViews() // Clear previous rows

        val dates = generateMondaysAndFridays(selectedYear, selectedMonth)

        addSubHeaderRow(tableLayout, dates)

        val addedNames = mutableSetOf<String>() // Track added names

        fetchAttendanceData(userIds, dates) { attendanceMap ->
            for ((index, name) in names.withIndex()) {
                if (!addedNames.contains(name.trim())) {
                    addDataRow(tableLayout, index + 1, name, dates, attendanceMap)
                    addedNames.add(name) // Mark name as added
                } else {
                    Log.d("DuplicateName", "Skipping duplicate name: $name")
                }
            }
        }
    }

    private fun fetchAttendanceData(
        userIds: List<String>,
        dates: List<String>,
        onComplete: (Map<String, Map<String, String>>) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val attendanceMap = mutableMapOf<String, MutableMap<String, String>>()
        // val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        var processedDates = 0

        val documentId = "$selectedYear-$selectedMonth"

        db.collection("tableheaders").document(documentId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // If document exists, use its headers field
                    val headers = document.get("headers") as? List<String>
                    if (headers != null) {
                        for (header in headers) {
                            val fullDate = "$selectedYear-$header"
                            db.collection("attendance").document(fullDate).collection("users")
                                .get()
                                .addOnSuccessListener { documents ->
                                    if (!attendanceMap.containsKey(fullDate)) {
                                        attendanceMap[fullDate] = mutableMapOf()
                                    }

                                    for (document in documents) {
                                        val userId = document.id
                                        val status = document.getString("status") ?: "A"
                                        if (userIds.contains(userId)) {
                                            attendanceMap[fullDate]?.put(userId, status)
                                        }
                                    }

                                    processedDates++
                                    if (processedDates == dates.size) {
                                        onComplete(attendanceMap)
                                    }
                                }
                                .addOnFailureListener {
                                    processedDates++
                                    if (processedDates == dates.size) {
                                        onComplete(attendanceMap)
                                    }
                                }
                        }
                    }
                } else {

                    for (date in dates) {
                        val fullDate = "$selectedYear-$date"
                        db.collection("attendance").document(fullDate).collection("users")
                            .get()
                            .addOnSuccessListener { documents ->
                                if (!attendanceMap.containsKey(fullDate)) {
                                    attendanceMap[fullDate] = mutableMapOf()
                                }

                                for (document in documents) {
                                    val userId = document.id
                                    val status = document.getString("status") ?: "A"
                                    if (userIds.contains(userId)) {
                                        attendanceMap[fullDate]?.put(userId, status)
                                    }
                                }

                                processedDates++
                                if (processedDates == dates.size) {
                                    onComplete(attendanceMap)
                                }
                            }
                            .addOnFailureListener {
                                processedDates++
                                if (processedDates == dates.size) {
                                    onComplete(attendanceMap)
                                }
                            }
                    }

                }
            }
    }

    private fun generateMondaysAndFridays(year: Int, month: Int): List<String> {
        val dates = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1) // Set to the 1st day of the month

        while (calendar.get(Calendar.MONTH) + 1 == month) {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.MONDAY || dayOfWeek == Calendar.FRIDAY) {
                dates.add(String.format("%02d-%02d", month, calendar.get(Calendar.DAY_OF_MONTH)))
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1) // Move to the next day
        }

        return dates
    }

    private fun addDataRow(
        tableLayout: TableLayout,
        rowNum: Int,
        name: String,
        dates: List<String>,
        attendanceMap: Map<String, Map<String, String>>
    ) {

        val db = FirebaseFirestore.getInstance()
        val documentId = "$selectedYear-$selectedMonth" // Format document ID as "year-month"

        // Check Firestore for the headers
        db.collection("tableheaders").document(documentId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // If document exists, use its headers field
                    val headers = document.get("headers") as? List<String>
                    if (headers != null) {
                        val userId = userIds1.getOrNull(rowNum - 1)

                        val dataRow = TableRow(this)

                        // Add row header with name
                        dataRow.addView(createTextViewForTableCell("$rowNum. $name", Color.BLACK, R.drawable.border, Gravity.LEFT))

                        for (header in headers) {
                            // Construct the full date
                            val fullDate = "$selectedYear-$header"
                            Log.d("addDataRow", "Processing date: $fullDate for userId: $userId")

                            // Get the attendance status
                            val status = attendanceMap[fullDate]?.get(userId) ?: "A"
                            Log.d("addDataRow", "Attendance status for $fullDate: $status")

                            // Determine the background drawable
                            val bgDrawable = if (status == "P") R.drawable.border_present else R.drawable.border

                            // Create a cell with the appropriate background
                            val inCell = createTextViewForTableCell(if (status == "P") " " else " ", Color.BLACK, bgDrawable, Gravity.CENTER)
                            Log.d("addDataRow", "Created cell for $fullDate with status: $status")

                            dataRow.addView(inCell)
                        }

                        tableLayout.addView(dataRow)
                        Log.d("addDataRow", "Row $rowNum added for $name")
                    }
                } else {
                    // If document doesn't exist, use the default dates
                    val userId = userIds1.getOrNull(rowNum - 1)

                    val dataRow = TableRow(this)

                    // Add row header with name
                    dataRow.addView(createTextViewForTableCell("$rowNum. $name", Color.BLACK, R.drawable.border, Gravity.LEFT))

                    for (date in dates) {
                        // Construct the full date
                        val fullDate = "$selectedYear-$date"
                        Log.d("addDataRow", "Processing date: $fullDate for userId: $userId")

                        // Get the attendance status
                        val status = attendanceMap[fullDate]?.get(userId) ?: "A"
                        Log.d("addDataRow", "Attendance status for $fullDate: $status")

                        // Determine the background drawable
                        val bgDrawable = if (status == "P") R.drawable.border_present else R.drawable.border

                        // Create a cell with the appropriate background
                        val inCell = createTextViewForTableCell(if (status == "P") " " else " ", Color.BLACK, bgDrawable, Gravity.CENTER)
                        Log.d("addDataRow", "Created cell for $fullDate with status: $status")

                        dataRow.addView(inCell)
                    }
                }
            }


        // Add the data row to the table

    }


    private fun createTextView(text: String, bgColor: Int, gravity: Int): TextView {
        return TextView(this).apply {
            this.text = text
            this.setPadding(20, 16, 20, 16)
            this.gravity = gravity
            this.setBackgroundResource(R.drawable.border)
            val params = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
            params.setMargins(1, 1, 1, 1)
        }
    }

    private fun createTextViewForTableCell(text: String, textColor: Int, bgDrawable: Int, gravity: Int): TextView {
        return TextView(this).apply {
            this.text = text
            this.setPadding(20, 16, 20, 16)
            this.gravity = gravity
            this.setBackgroundResource(bgDrawable) // Apply the background drawable
            this.setTextColor(textColor) // Set text color
            val params = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
            params.setMargins(1, 1, 1, 1)
        }
    }

    suspend fun fetchNotesAndDepartment(): Pair<String, String?> {
        return withContext(Dispatchers.IO) {
            val db = FirebaseFirestore.getInstance()

            val date = "$selectedYear-$selectedMonth"
            var notes: String = "Error fetching notes."
            var fetchedDepartment: String? = null

            Log.d("FetchNotes", "Fetching current user details")

            try {
                Log.d("FetchNotes", "Fetching current user details")
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val userId = currentUser.uid
                    Log.d("FetchNotes", "Current user ID: $userId")

                    val userDocument = Tasks.await(db.collection("users").document(userId).get())
                    if (userDocument.exists()) {
                        fetchedDepartment = userDocument.getString("department")
                        Log.d("FetchNotes", "Fetched department: $fetchedDepartment")
                    } else {
                        fetchedDepartment = "Department not found."
                        Log.w("FetchNotes", "User document does not exist.")
                    }

                    Log.w("FetchNotes", "$fetchedDepartment")
                } else {
                    fetchedDepartment = "User not logged in"
                    Log.e("FetchNotes", "No current user logged in.")
                }

                if (!fetchedDepartment.isNullOrEmpty() && fetchedDepartment != "Department not found.") {
                    Log.d("FetchNotes", "Fetching notes for department: $fetchedDepartment and date: $date")

                    val attendanceDocRef = db.collection("attendance")
                        .document(date)
                        .collection("department")
                        .document(fetchedDepartment)

                    val document = Tasks.await(attendanceDocRef.get())
                    notes = if (document.exists()) {
                        document.getString("notes") ?: "No notes found for this department."
                    } else {
                        ""
                    }
                    Log.d("FetchNotes", "Fetched notes: $notes")
                } else {
                    notes = ""
                    Log.w("FetchNotes", "Invalid or missing department.")
                }
            } catch (e: Exception) {
                Log.e("FetchNotes", "Error fetching data: ${e.message}", e)
                notes = "Error fetching notes: ${e.message}"
                fetchedDepartment = "Error fetching department: ${e.message}"
            }

            Log.d("FetchNotes", "Returning notes and department: notes = $notes, department = $fetchedDepartment")
            Pair(notes, fetchedDepartment)
        }
    }

    private fun createPdf(pdfTable: Table) {
        try {
        val state = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED != state) {
            Toast.makeText(this, "External storage is not writable", Toast.LENGTH_SHORT).show()
            return
        }

        pdfFilePath = getPdfFilePath()
        newFilePath = getNewPdfFilePath(pdfFilePath.toString().trim())

        val assetManager = assets
        val inputStream = assetManager.open("FlagCeremony-Monitoring.pdf")
        val reader = PdfReader(inputStream)
        val pdfWriter = PdfWriter(newFilePath)
        val pdfDocument = PdfDocument(reader, pdfWriter)

        val document = Document(pdfDocument)

        val pageWidth = pdfDocument.getPage(1).pageSize.width

        pdfTable.setWidth(UnitValue.createPercentValue(80f))

        val width = pdfDocument.getPage(1).pageSize.width
        val height = pdfDocument.getPage(1).pageSize.height

        val tableWidth = width * 0.8f // 80% of page width

        val tableXPosition = (width - tableWidth) / 2

        pdfTable.setFixedPosition(tableXPosition, tableYPosition, tableWidth)

            // val tableYPosition = 385f
        val monthNameSelected = selectedMonthName
        val yearSelected = selectedYear

        // Create a paragraph with text content
        val currentDate = Paragraph("$monthNameSelected $yearSelected")
            .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD))  // Set font
            .setFontSize(11f)  // Set font size
            .setUnderline(1f, -1f)
            .setTextAlignment(TextAlignment.CENTER)  // Align text to center

        // Set the Y position (choose any position as needed)
        val currentDateYPosition = 640f

        // Add the paragraph at the calculated fixed position
        currentDate.setFixedPosition(0f, currentDateYPosition, pageWidth)  // The 0f is for horizontal start position (left aligned for centering)
        document.add(currentDate)

        document.add(pdfTable)

        val arialNarrow = PdfFontFactory.createFont("assets/robotocondensed-regular.ttf", PdfEncodings.WINANSI, true)
        val arialNarrowBold = PdfFontFactory.createFont("assets/RobotoCondensed-Bold.ttf", PdfEncodings.WINANSI, true)

        lifecycleScope.launch {
            try {
                val (notesContent, department) = fetchNotesAndDepartment()

                var currentDepartment = ""
                var chairperson = ""
                var dean = ""
                var chairpersonDepartment = ""
                var deanDepartment = ""

                if (department.equals("CCIS")) {
                    currentDepartment = "Computing and Information Sciences"
                    chairperson = "CATHERINE LEAH G. GABO"
                    dean = "SHELLA C. OLAGUIR, PhD"
                    chairpersonDepartment = "CIS"
                    deanDepartment = "CCIS"
                } else if (department.equals("CTAS")) {
                    currentDepartment = "Technology and Allied Sciences"
                    chairperson = "GHENA L. ASUCAN"
                    dean = "JUN REY J. POYOS"
                    chairpersonDepartment = "CTAS"
                    deanDepartment = "CTAS"
                } else {
                    currentDepartment = "Criminal Justice"
                    chairperson = "MARRY JOYCE G. ALE, PhD"
                    dean = "MARRY JOYCE G. ALE, PhD"
                    chairpersonDepartment = "CCJ"
                    deanDepartment = "CCJ"
                }

                val collegeTxt = Paragraph("College of " + currentDepartment)
                    .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))  // Set font
                    .setFontSize(9f)  // Set font size
                    .setTextAlignment(TextAlignment.CENTER)  // Align text to center

                val collegeTxtYPosition = 677f

                collegeTxt.setFixedPosition(0f, collegeTxtYPosition, pageWidth)  // The 0f is for horizontal start position (left aligned for centering)
                document.add(collegeTxt)

                val departmentTxt = Paragraph("Department of " + currentDepartment)
                    .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA))  // Set font
                    .setFontSize(9f)  // Set font size
                    .setTextAlignment(TextAlignment.CENTER)  // Align text to center

                // Set the Y position (choose any position as needed)
                val departmentTxtYPosition = 667f

                // Add the paragraph at the calculated fixed position
                departmentTxt.setFixedPosition(0f, departmentTxtYPosition, pageWidth)  // The 0f is for horizontal start position (left aligned for centering)
                document.add(departmentTxt)

                // Notes
                val notesDisplay = Paragraph(notesContent)
                    .setFont(PdfFontFactory.createFont(StandardFonts.HELVETICA)) // Set font
                    .setFontSize(12f)
                    .setPaddingLeft(-2.6f)
                    .setFixedLeading(14f)

                val notesLineOneWidth = width * 0.8f // 80% of page width

                // Calculate the X position to center the table
                val notesLineOneWidthXPosition = (width - notesLineOneWidth) / 2

                // Set the Y position for the table (adjust as necessary)
                val notesLineOneWidthYPosition = 218f  // Adjust as necessary

                // Position the table
                notesDisplay.setFixedPosition(notesLineOneWidthXPosition, notesLineOneWidthYPosition, notesLineOneWidth)

                document.add(notesDisplay)

                val table1 = Table(UnitValue.createPercentArray(floatArrayOf(40f)))
                    .setWidth(UnitValue.createPercentValue(40f))

                val chairpersonName = Paragraph(chairperson)
                    .setFont(arialNarrowBold)
                    .setFontSize(10f)
                    .setTextAlignment(TextAlignment.CENTER)

                val chairpersonLabel = Paragraph("Chairperson, $chairpersonDepartment")
                    .setFont(arialNarrow)
                    .setFontSize(10f)
                    .setCharacterSpacing(-0.17f)
                    .setTextAlignment(TextAlignment.CENTER)

                table1.addCell(Cell().add(chairpersonName).setBorder(null))
                table1.addCell(Cell().add(chairpersonLabel).setBorder(null))

                val preparedAndMonitoredByLabel = Paragraph()
                    .add(table1)
                    .setPaddingLeft(-2.6f)
                    .setFixedLeading(7.25f)

                val preparedAndMonitoredByLabelYPosition = 42.4f

                preparedAndMonitoredByLabel.setFixedPosition(0f, preparedAndMonitoredByLabelYPosition, pageWidth)  // The 0f is for horizontal start position (left aligned for centering)
                document.add(preparedAndMonitoredByLabel)

                val table2 = Table(UnitValue.createPercentArray(floatArrayOf(50f)))
                    .setWidth(UnitValue.createPercentValue(50f))  // Adjust as needed

                val deanName = Paragraph(dean)
                    .setFont(arialNarrowBold)
                    .setFontSize(10f)
                    .setTextAlignment(TextAlignment.CENTER)

                val deanLabel = Paragraph("Dean, $deanDepartment")
                    .setFont(arialNarrow)
                    .setFontSize(10f)
                    .setCharacterSpacing(-0.17f)
                    .setTextAlignment(TextAlignment.CENTER)

                table2.addCell(Cell().add(deanName).setBorder(null))
                table2.addCell(Cell().add(deanLabel).setBorder(null))

                val approveByLabel = Paragraph()
                    .add(table2)
                    .setPaddingLeft(87.3f)
                    .setFixedLeading(7.25f)

                val approveByLabelYPosition = 42.4f

                approveByLabel.setFixedPosition(209.98f, approveByLabelYPosition, pageWidth)
                document.add(approveByLabel)

                // Close the document
                document.close()
                reader.close()
                pdfWriter.close()

                // Show a success message
                Toast.makeText(this@ViewReport, "PDF created successfully: $newFilePath", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // Handle errors gracefully
                Log.e("PDFCreation", "Error creating PDF: ${e.message}", e)
                Toast.makeText(this@ViewReport, "Error creating PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to create PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPdfFilePath(): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return "${downloadsDir.absolutePath}/$selectedMonthName$selectedYear.pdf"
    }

    private fun getNewPdfFilePath(originalPath: String): String {
        val file = File(originalPath)
        return if (!file.exists()) {
            originalPath.replace(".pdf", "_${System.currentTimeMillis()}.pdf")
        } else {
            val timestamp = System.currentTimeMillis()
            originalPath.replace(".pdf", "_${timestamp}.pdf")
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val documentId = "$selectedYear-$selectedMonth"
        var dates: List<String> = listOf() // Use an immutable List initially

        db.collection("tableheaders").document(documentId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val headers = document.get("headers") as? List<String>
                    if (headers != null) {
                        // Reassign dates to the fetched headers (immutable list)
                        dates = headers
                    }
                } else {
                    // Reassign dates to the result of generateMondaysAndFridays (immutable list)
                    dates = generateMondaysAndFridays(selectedYear, selectedMonth)
                }

                // Proceed with attendance data and PDF creation after Firestore data is fetched
                if (requestCode == STORAGE_PERMISSION_CODE) {
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        fetchAttendanceData(userIds, dates) { attendanceMap ->
                            val pdfTable = createPdfTable(userIds, nameLists, dates, attendanceMap)
                            createPdf(pdfTable) // Function to save and display the PDF
                        }
                    } else {
                        Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreError", "Error fetching document", e)
                Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
    }


    override fun onBackPressed() {
        val intent = Intent(applicationContext, Chairperson::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        finish()
        super.onBackPressed()
    }
}