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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPreferences = getSharedPreferences("CallStats", Context.MODE_PRIVATE)
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
                // Parse Excel file
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
                // Process as TXT/CSV
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

            // Save imported data
            val filename = "$importKey.txt"
            openFileOutput(filename, Context.MODE_PRIVATE).use { output ->
                importedLines.forEach { line ->
                    output.write((line + "\n").toByteArray())
                }
            }

            importStatsMap[importKey] = ImportStats(total = importCount)
            saveImportStats(importKey)

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
        onCallCompleted()
    }

    private fun onCallCompleted() {
        showAnnotationDialog()
    }

    private fun showAnnotationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("请选择标注类型")
        builder.setPositiveButton("客户") { _, _ ->
            showRemarkDialog()
        }
        builder.setNegativeButton("无用") { _, _ ->
            phoneList[currentCallIndex].annotation = "无用"
            updateStatsForCurrent("useless")
            showToast("标注为无用")
            currentCallIndex++
            autoDialNext()
        }
        builder.show()
    }

    private fun showRemarkDialog() {
        val editText = EditText(this)
        val builder = AlertDialog.Builder(this)
        builder.setTitle("输入备注信息")
        builder.setView(editText)
        builder.setPositiveButton("确定") { _, _ ->
            val remark = editText.text.toString()
            phoneList[currentCallIndex].annotation = "客户"
            phoneList[currentCallIndex].remark = remark
            updateStatsForCurrent("customer")
            showToast("标注为客户，备注：$remark")
            currentCallIndex++
            autoDialNext()
        }
        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun updateStatsForCurrent(type: String) {
        val entry = phoneList[currentCallIndex]
        val key = entry.importKey ?: return
        val stat = importStatsMap.getOrPut(key) { ImportStats() }
        when (type) {
            "dialed" -> stat.dialed++
            "customer" -> stat.customer++
            "useless" -> stat.useless++
        }
        saveImportStats(key)
    }

    private fun saveImportStats(key: String) {
        val stat = importStatsMap[key] ?: return
        val json = "${stat.total},${stat.dialed},${stat.customer},${stat.useless}"
        val filename = "${key}_stat.txt"
        openFileOutput(filename, Context.MODE_PRIVATE).use {
            it.write(json.toByteArray())
        }
    }

    private fun autoDialNext() {
        if (currentCallIndex < phoneList.size) {
            if (isPaused) {
                showToast("拨号已暂停")
                return
            }
            handler.postDelayed({ startCall() }, 5000)
        }
    }

    private fun toggleFloatingWindow() {
        if (floatingWindowVisible) {
            removeFloatingWindow()
        } else {
            showFloatingWindow()
        }
    }

    private fun showFloatingWindow() {
        val inflater = LayoutInflater.from(this)
        val floatingView = inflater.inflate(R.layout.floating_window, null)

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        layoutParams.gravity = Gravity.TOP or Gravity.END
        layoutParams.x = 0
        layoutParams.y = 100

        floatingView.btnClose.setOnClickListener {
            removeFloatingWindow()
        }
        floatingView.btnRestore.setOnClickListener {
            removeFloatingWindow()
            showToast("恢复主界面")
        }

        windowManager.addView(floatingView, layoutParams)
        floatingWindowVisible = true
        showToast("显示浮动窗口")
    }

    private fun removeFloatingWindow() {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val floatingView = findViewById<View>(R.id.floating_window_layout)
        if (floatingView != null) {
            windowManager.removeViewImmediate(floatingView)
            floatingWindowVisible = false
            showToast("浮动窗口已关闭")
        }
    }

    private fun updateStats() {
        showToast("总拨号次数: $totalCalls, 当天拨号次数: $dailyCalls")
    }

    private fun loadStats() {
        totalCalls = sharedPreferences.getInt("total_calls", 0)
        dailyCalls = sharedPreferences.getInt("daily_calls", 0)
        showToast("加载统计数据：总拨号次数 $totalCalls，当天拨号次数 $dailyCalls")
    }

    private fun saveStats() {
        with(sharedPreferences.edit()) {
            putInt("total_calls", totalCalls)
            putInt("daily_calls", dailyCalls)
            apply()
        }
    }

    private fun checkPermission(permission: String) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 1)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    data class PhoneEntry(
        val number: String,
        var annotation: String = "未标注",
        var remark: String? = null,
        val importKey: String? = null
    )

    data class ImportStats(
        var total: Int = 0,
        var dialed: Int = 0,
        var customer: Int = 0,
        var useless: Int = 0
    )
}