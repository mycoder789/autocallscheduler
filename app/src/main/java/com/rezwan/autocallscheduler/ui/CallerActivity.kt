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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.SeekBar
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

    private val CALL_PHONE = Manifest.permission.CALL_PHONE
    private val phoneList = mutableListOf<PhoneEntry>()
    private var currentCallIndex = 0
    private var totalCalls = 0
    private var dailyCalls = 0
    private var floatingWindowVisible = false
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var isPaused = false

    private var currentImportKey: String? = null
    private val importStatsMap = mutableMapOf<String, ImportStats>()

    // 默认拨号间隔时间（以毫秒为单位）
    private var dialInterval: Long = 5000

    // Firebase Firestore 实例
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPreferences = getSharedPreferences("CallStats", Context.MODE_PRIVATE)
        dialInterval = sharedPreferences.getLong("dial_interval", 5000) // 加载拨号间隔设置
        initListeners()
        loadStats()
    }

    private fun initListeners() {
        btnStartCall.setOnClickListener { startCall() }
        btnImportFile.setOnClickListener { selectFileForImport() }
        btnShowFloating.setOnClickListener { toggleFloatingWindow() }

        btnPauseResume.setOnClickListener {
            isPaused = !isPaused
            if (isPaused) {
                btnPauseResume.text = "继续拨号"
                showToast("拨号已暂停")
            } else {
                btnPauseResume.text = "暂停拨号"
                showToast("拨号已恢复")
                autoDialNext()
            }
        }

        btnSkipDelay.setOnClickListener { skipDelayToNextCall() }

        btnSetInterval.setOnClickListener { showIntervalDialog() }
    }

    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleFileImport(it) }
    }

    private fun selectFileForImport() {
        importFileLauncher.launch("*/*")
    }

    private fun handleFileImport(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val importedLines = mutableListOf<String>()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val importKey = "import_$timestamp"
            currentImportKey = importKey
            var importCount = 0

            val fileName = uri.lastPathSegment ?: "unknown"
            if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
                // 解析 Excel 文件
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
                        importCount++
                    }
                }
                workbook.close()
            } else {
                // 处理 TXT/CSV 文件
                val reader = BufferedReader(InputStreamReader(inputStream))
                reader.useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.trim().split(",")
                        if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                            val number = parts[0].replace("\\s".toRegex(), "")
                            val remark = if (parts.size > 1) parts[1] else null
                            phoneList.add(PhoneEntry(number = number, remark = remark, importKey = importKey))
                            importedLines.add(line.trim())
                            importCount++
                        }
                    }
                }
            }

            // 保存导入数据
            val filename = "$importKey.txt"
            openFileOutput(filename, Context.MODE_PRIVATE).use { output ->
                importedLines.forEach { line ->
                    output.write((line + "\n").toByteArray())
                }
            }

            importStatsMap[importKey] = ImportStats(total = importCount)
            saveImportStats(importKey)

            // 同步导入数据到云端
            syncContactsToCloud()

            showToast("导入成功，共 $importCount 个号码，保存为 $filename")

        } catch (e: Exception) {
            showToast("导入失败：${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startCall() {
        if (currentCallIndex >= phoneList.size) {
            showToast("所有号码已拨打完成！")
            return
        }

        if ((currentCallIndex + 1) % 30 == 0) {
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

        // 同步拨号记录到云端
        syncCallLogsToCloud(phoneNo)

        onCallCompleted()
    }

    private fun autoDialNext() {
        if (currentCallIndex < phoneList.size) {
            if (isPaused) {
                showToast("拨号已暂停")
                return
            }
            handler.postDelayed({ startCall() }, dialInterval) // 使用自定义拨号间隔
        }
    }

    private fun showIntervalDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_set_interval, null)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekBarInterval)
        val builder = AlertDialog.Builder(this)
        builder.setTitle("设置拨号间隔")
        builder.setView(dialogView)
        builder.setPositiveButton("确定") { _, _ ->
            dialInterval = (seekBar.progress * 1000).toLong()
            sharedPreferences.edit().putLong("dial_interval", dialInterval).apply()
            showToast("拨号间隔已设置为 ${dialInterval / 1000} 秒")
        }
        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun syncContactsToCloud() {
        val contacts = phoneList.map { it.toMap() }
        firestore.collection("contacts").add(contacts)
            .addOnSuccessListener { showToast("联系人已同步至云端") }
            .addOnFailureListener { showToast("联系人同步失败") }
    }

    private fun syncCallLogsToCloud(phoneNo: String) {
        val log = mapOf(
            "phone_number" to phoneNo,
            "timestamp" to System.currentTimeMillis()
        )
        firestore.collection("call_logs").add(log)
            .addOnSuccessListener { showToast("拨号记录已同步至云端") }
            .addOnFailureListener { showToast("拨号记录同步失败") }
    }

    // 其他代码保持不变...

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
}