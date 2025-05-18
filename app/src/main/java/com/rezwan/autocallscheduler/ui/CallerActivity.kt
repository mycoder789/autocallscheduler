package com.rezwan.autocallscheduler.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rezwan.autocallscheduler.R
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.floating_window.view.*
import java.io.BufferedReader
import java.io.InputStreamReader

class CallerActivity : BaseActivity() {

    private val CALL_PHONE = Manifest.permission.CALL_PHONE
    private val phoneList = mutableListOf<PhoneEntry>() // 电话号码列表，包含标注信息
    private var currentCallIndex = 0
    private var totalCalls = 0 // 总拨号次数
    private var dailyCalls = 0 // 当天拨号次数
    private var floatingWindowVisible = false // 浮动窗口是否显示
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper()) // 用于自动连续拨号

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPreferences = getSharedPreferences("CallStats", Context.MODE_PRIVATE)
        initListeners()
        loadStats() // 加载统计数据
    }

    private fun initListeners() {
        btnStartCall.setOnClickListener { startCall() }
        btnImportFile.setOnClickListener { selectFileForImport() }
        btnShowFloating.setOnClickListener { toggleFloatingWindow() }
    }

    @SuppressLint("MissingPermission")
    private fun startCall() {
        if (currentCallIndex >= phoneList.size) {
            showToast("所有号码已拨打完成！")
            return
        }

        val phoneNo = phoneList[currentCallIndex].number
        showToast("正在拨打：$phoneNo")
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNo")))

        // 更新统计数据
        totalCalls++
        dailyCalls++
        saveStats()
        updateStats()

        // 模拟拨号完成后调用标注功能
        onCallCompleted()
    }

    private fun onCallCompleted() {
        // 显示标注对话框
        showAnnotationDialog()
    }

    private fun showAnnotationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("请选择标注类型")
        builder.setPositiveButton("客户") { _, _ ->
            // 显示备注输入框
            showRemarkDialog()
        }
        builder.setNegativeButton("无用") { _, _ ->
            // 标注为无用
            phoneList[currentCallIndex].annotation = "无用"
            showToast("标注为无用")
            currentCallIndex++ // 跳到下一个号码
            autoDialNext() // 自动拨打下一个号码
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
            showToast("标注为客户，备注：$remark")
            currentCallIndex++ // 跳到下一个号码
            autoDialNext() // 自动拨打下一个号码
        }
        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun selectFileForImport() {
        val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleFileImport(it) }
        }
        importFileLauncher.launch("text/*") // 选择 TXT 或 CSV 文件
    }

    private fun handleFileImport(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            phoneList.clear()

            reader.useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split(",") // 假设 CSV 格式为 "号码,备注"
                    val phoneEntry = PhoneEntry(
                        number = parts[0],
                        annotation = "未标注",
                        remark = if (parts.size > 1) parts[1] else null
                    )
                    phoneList.add(phoneEntry)
                }
            }

            showToast("文件导入成功，共${phoneList.size}个号码")
        } catch (e: Exception) {
            showToast("文件导入失败：${e.message}")
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
        try {
            val floatingView = findViewById<View>(R.id.floating_window_layout) // 修正类型为 View
            windowManager.removeViewImmediate(floatingView)
            floatingWindowVisible = false
            showToast("浮动窗口已关闭")
        } catch (e: Exception) {
            showToast("浮动窗口关闭失败：${e.message}")
        }
    }

    private fun updateStats() {
        // 显示统计数据
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

    private fun autoDialNext() {
        if (currentCallIndex < phoneList.size) {
            handler.postDelayed({ startCall() }, 2000) // 延迟 2 秒拨打下一个号码
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
        var remark: String? = null
    )
}