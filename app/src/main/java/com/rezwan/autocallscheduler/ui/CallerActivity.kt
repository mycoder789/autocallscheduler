package com.rezwan.autocallscheduler.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.rezwan.autocallscheduler.R
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.floating_window.view.*
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class CallerActivity : BaseActivity() {

    // Constants
    private val CALL_PHONE_PERMISSION = Manifest.permission.CALL_PHONE
    private val DEFAULT_DIAL_INTERVAL_MS: Long = 5000

    // Variables
    private val phoneList = mutableListOf<PhoneEntry>()
    private var currentCallIndex = 0
    private var totalCalls = 0
    private var dailyCalls = 0
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isPaused = false
    private var dialInterval: Long = DEFAULT_DIAL_INTERVAL_MS

    // Firebase Firestore instance
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize shared preferences and load settings
        sharedPreferences = getSharedPreferences("CallStats", Context.MODE_PRIVATE)
        dialInterval = sharedPreferences.getLong("dial_interval", DEFAULT_DIAL_INTERVAL_MS)

        initListeners()
        loadStats()
    }

    /**
     * Initializes button listeners for the activity.
     */
    private fun initListeners() {
        btnStartCall.setOnClickListener { startCall() }
        btnImportFile.setOnClickListener { selectFileForImport() }
        btnShowFloating.setOnClickListener { toggleFloatingWindow() }
        btnPauseResume.setOnClickListener { togglePauseResume() }
        btnSkipDelay.setOnClickListener { skipDelayToNextCall() }
        btnSetInterval.setOnClickListener { showDialIntervalInputDialog() }
    }

    /**
     * Toggles the pause and resume state for auto-dialing.
     */
    private fun togglePauseResume() {
        isPaused = !isPaused
        btnPauseResume.text = if (isPaused) "继续拨号" else "暂停拨号"
        showToast(if (isPaused) "拨号已暂停" else "拨号已恢复")
        if (!isPaused) autoDialNext()
    }

    /**
     * Handles file import logic for phone numbers.
     */
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFileImport(it) }
    }

    private fun selectFileForImport() {
        importFileLauncher.launch("*/*")
    }

    /**
     * Processes the imported file and extracts phone numbers.
     */
    private fun handleFileImport(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val importedLines = mutableListOf<String>()
            val timestamp = getCurrentTimestamp()
            val importKey = "import_$timestamp"
            var importCount = 0

            val fileName = uri.lastPathSegment ?: "unknown"
            if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
                importCount = processExcelFile(inputStream, importKey, importedLines)
            } else {
                importCount = processTextFile(inputStream, importKey, importedLines)
            }

            saveImportedData(importKey, importedLines)
            showToast("导入成功，共 $importCount 个号码")

            // Sync imported data to cloud
            syncContactsToCloud()

        } catch (e: Exception) {
            showToast("导入失败：${e.message}")
        }
    }

    private fun processExcelFile(inputStream: java.io.InputStream?, importKey: String, importedLines: MutableList<String>): Int {
        var count = 0
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0)
        for (row in sheet) {
            val numberCell = row.getCell(0)
            val remarkCell = if (row.physicalNumberOfCells > 1) row.getCell(1) else null
            val number = numberCell?.toString()?.trim()
            val remark = remarkCell?.toString()?.trim()

            if (!number.isNullOrBlank()) {
                phoneList.add(PhoneEntry(number = number, remark = remark, importKey = importKey))
                importedLines.add("$number,${remark ?: ""}")
                count++
            }
        }
        workbook.close()
        return count
    }

    private fun processTextFile(inputStream: java.io.InputStream?, importKey: String, importedLines: MutableList<String>): Int {
        var count = 0
        val reader = BufferedReader(InputStreamReader(inputStream))
        reader.useLines { lines ->
            lines.forEach { line ->
                val parts = line.trim().split(",")
                if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                    val number = parts[0].replace("\\s".toRegex(), "")
                    val remark = if (parts.size > 1) parts[1] else null
                    phoneList.add(PhoneEntry(number = number, remark = remark, importKey = importKey))
                    importedLines.add(line.trim())
                    count++
                }
            }
        }
        return count
    }

    private fun saveImportedData(importKey: String, importedLines: List<String>) {
        val filename = "$importKey.txt"
        openFileOutput(filename, Context.MODE_PRIVATE).use { output ->
            importedLines.forEach { line ->
                output.write((line + "\n").toByteArray())
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCall() {
        if (currentCallIndex >= phoneList.size) {
            showToast("所有号码已拨打完成！")
            return
        }

        if ((currentCallIndex + 1) % 30 == 0) {
            showPauseDialog()
            return
        }

        val phoneNo = phoneList[currentCallIndex].number
        showToast("正在拨打：$phoneNo")
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNo")))

        totalCalls++
        dailyCalls++
        updateStatsForCurrent("dialed")
        saveStats()
        updateStats()

        // Sync call logs to cloud
        syncCallLogsToCloud(phoneNo)

        onCallCompleted()
    }

    private fun showPauseDialog() {
        isPaused = true
        btnPauseResume.text = "继续拨号"
        AlertDialog.Builder(this)
            .setTitle("拨号提醒")
            .setMessage("您已拨打 ${currentCallIndex + 1} 个号码，是否继续？")
            .setPositiveButton("继续") { _, _ ->
                isPaused = false
                btnPauseResume.text = "暂停拨号"
                autoDialNext()
            }
            .setNegativeButton("暂停") { _, _ ->
                showToast("拨号已暂停")
            }
            .show()
    }

    private fun autoDialNext() {
        if (currentCallIndex < phoneList.size && !isPaused) {
            handler.postDelayed({ startCall() }, dialInterval)
        }
    }

    private fun showDialIntervalInputDialog() {
        val editText = EditText(this)
        editText.hint = "输入拨号间隔时间（秒）"
        val builder = AlertDialog.Builder(this)
        builder.setTitle("设置拨号间隔")
        builder.setView(editText)
        builder.setPositiveButton("确定") { _, _ ->
            val input = editText.text.toString()
            val interval = input.toLongOrNull()
            if (interval != null && interval > 0) {
                dialInterval = interval * 1000
                sharedPreferences.edit().putLong("dial_interval", dialInterval).apply()
                showToast("拨号间隔已设置为 $interval 秒")
            } else {
                showToast("请输入有效的数字")
            }
        }
        builder.setNegativeButton("取消") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    private fun syncContactsToCloud() {
        val contacts = phoneList.map { it.toMap() }
        firestore.collection("contacts").add(contacts)
            .addOnSuccessListener { showToast("联系人已同步至云端") }
            .addOnFailureListener { showToast("联系人同步失败") }
    }

    private fun syncCallLogsToCloud(phoneNo: String) {
        val log = mapOf("phone_number" to phoneNo, "timestamp" to System.currentTimeMillis())
        firestore.collection("call_logs").add(log)
            .addOnSuccessListener { showToast("拨号记录已同步至云端") }
            .addOnFailureListener { showToast("拨号记录同步失败") }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateStatsForCurrent(type: String) {
        val entry = phoneList[currentCallIndex]
        val key = entry.importKey ?: return
        val stat = importStatsMap.getOrPut(key) { ImportStats() }
        when (type) {
            "dialed" -> stat.dialed++
        }
        saveImportStats(key)
    }

    // Data Classes
    data class PhoneEntry(
        val number: String,
        var annotation: String = "未标注",
        var remark: String? = null,
        val importKey: String? = null
    ) {
        fun toMap(): Map<String, Any?> {
            return mapOf(
                "number" to number,
                "annotation" to annotation,
                "remark" to remark,
                "importKey" to importKey
            )
        }
    }

    data class ImportStats(
        var total: Int = 0,
        var dialed: Int = 0
    )
}