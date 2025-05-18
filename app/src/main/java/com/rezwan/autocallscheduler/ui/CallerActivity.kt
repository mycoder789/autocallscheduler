package com.rezwan.autocallscheduler.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rezwan.autocallscheduler.R
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.floating_window.view.*

class CallerActivity : BaseActivity() {

    private val CALL_PHONE = Manifest.permission.CALL_PHONE
    private val phoneList = mutableListOf<PhoneEntry>() // 电话号码列表，包含标注信息
    private var currentCallIndex = 0
    private var totalCalls = 0 // 总拨号次数
    private var dailyCalls = 0 // 当天拨号次数
    private var floatingWindowVisible = false // 浮动窗口是否显示

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initListeners()
        loadStats() // 加载统计数据
    }

    private fun initListeners() {
        btnStartCall.setOnClickListener { startCall() }
        btnImportFile.setOnClickListener { handleFileImport() }
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
        updateStats()

        // 模拟拨号完成后调用标注功能
        onCallCompleted()
    }

    private fun onCallCompleted() {
        // 显示标注对话框
        showAnnotationDialog()
    }

    private fun showAnnotationDialog() {
        val options = arrayOf("无标注", "标注无用", "标注客户")
        val builder = AlertDialog.Builder(this)
        builder.setTitle("请选择标注类型")
        builder.setItems(options) { _, which ->
            when (which) {
                0 -> {
                    // 无标注
                    phoneList[currentCallIndex].annotation = "无标注"
                    showToast("标注为无标注")
                }
                1 -> {
                    // 标注无用
                    phoneList[currentCallIndex].annotation = "无用"
                    showToast("标注为无用")
                }
                2 -> {
                    // 标注客户
                    showRemarkDialog()
                }
            }
            currentCallIndex++ // 跳到下一个号码
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
        }
        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun handleFileImport() {
        // 文件导入逻辑（TXT、CSV、Excel）
        showToast("导入文件功能")
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
        val floatingView = findViewById<WindowManager.LayoutParams>(R.id.floating_window_layout)
        if (floatingView != null) {
            windowManager.removeViewImmediate(floatingView)
            floatingWindowVisible = false
            showToast("浮动窗口已关闭")
        }
    }

    private fun updateStats() {
        // 显示统计数据
        showToast("总拨号次数: $totalCalls, 当天拨号次数: $dailyCalls")
    }

    private fun loadStats() {
        // 模拟加载统计数据
        totalCalls = 0 // 假设从持久化存储中加载的值
        dailyCalls = 0 // 假设从持久化存储中加载的值
        showToast("加载统计数据：总拨号次数 $totalCalls，当天拨号次数 $dailyCalls")
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