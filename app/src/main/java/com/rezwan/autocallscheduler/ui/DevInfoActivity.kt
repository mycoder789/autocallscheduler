package com.rezwan.autocallscheduler.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.rezwan.autocallscheduler.R
import com.rezwan.autocallscheduler.utils.BrowserUtils
import kotlinx.android.synthetic.main.activity_dev_info.*
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileReader

class DevInfoActivity : AppCompatActivity() {

    private val phoneNumbers = mutableListOf<String>() // 存储电话号码列表
    private var currentIndex = 0 // 当前拨打的号码索引
    private val handler = Handler(Looper.getMainLooper()) // 用于延迟拨打
    private var isPaused = false // 是否暂停拨打
    private var isStopped = false // 是否停止拨打

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_info)

        // 初始化界面和按钮监听
        initToolbar()
        initListener()
        tvDevLink.setHtml("<a href=\"https://github.com/mycoder789\">https://github.com/mycoder789</a>")

        // 显示版本号和联系方式
        displayAppInfo()

        // 按钮：导入电话号码
        findViewById<Button>(R.id.btnImportNumbers).setOnClickListener {
            pickFile()
        }

        // 按钮：开始拨打
        findViewById<Button>(R.id.btnStartDialing).setOnClickListener {
            if (phoneNumbers.isEmpty()) {
                Toast.makeText(this, "请先导入电话号码列表！", Toast.LENGTH_SHORT).show()
            } else {
                isPaused = false
                isStopped = false
                startAutoDialing()
            }
        }

        // 按钮：暂停拨打
        findViewById<Button>(R.id.btnPauseDialing).setOnClickListener {
            isPaused = true
            Toast.makeText(this, "拨打已暂停", Toast.LENGTH_SHORT).show()
        }

        // 按钮：停止拨打
        findViewById<Button>(R.id.btnStopDialing).setOnClickListener {
            isStopped = true
            Toast.makeText(this, "拨打已停止", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initToolbar() {
        supportActionBar?.apply {
            title = "关于开发者"
            elevation = 0f
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun initListener() {
        btnDev.setOnClickListener {
            BrowserUtils.openBrowser(this, "https://github.com/mycoder789")
        }

        btnProject.setOnClickListener {
            BrowserUtils.openBrowser(this, "https://github.com/mycoder789/autocallscheduler")
        }
    }

    private fun displayAppInfo() {
        // 获取应用版本号
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "未知版本"
        }

        // 设置开发者联系方式
        val contactInfo = "电话: 18027303007\n微信号: HKpetro"

        // 显示版本号
        findViewById<TextView>(R.id.tvAppVersion).text = "版本号: $version"
        // 显示联系方式
        findViewById<TextView>(R.id.tvContactInfo).text = contactInfo
    }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val filePath = it.path ?: return@registerForActivityResult
                processFile(filePath)
            }
        }

    private fun pickFile() {
        filePickerLauncher.launch("*/*") // 允许选择任意文件类型
    }

    private fun processFile(filePath: String) {
        when {
            filePath.endsWith(".txt", ignoreCase = true) -> loadPhoneNumbersFromTxt(filePath)
            filePath.endsWith(".csv", ignoreCase = true) -> loadPhoneNumbersFromCSV(filePath)
            filePath.endsWith(".xls", ignoreCase = true) || filePath.endsWith(".xlsx", ignoreCase = true) -> loadPhoneNumbersFromExcel(filePath)
            else -> Toast.makeText(this, "不支持的文件格式！", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPhoneNumbersFromTxt(filePath: String) {
        try {
            File(filePath).forEachLine { line ->
                phoneNumbers.add(line.trim())
            }
            Toast.makeText(this, "成功加载 ${phoneNumbers.size} 个电话号码！", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "加载 TXT 文件失败！", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPhoneNumbersFromCSV(filePath: String) {
        try {
            val reader = BufferedReader(FileReader(filePath))
            reader.forEachLine { line ->
                val phoneNumber = line.split(",")[0].trim() // 假设电话号码在第一列
                phoneNumbers.add(phoneNumber)
            }
            reader.close()
            Toast.makeText(this, "成功加载 ${phoneNumbers.size} 个电话号码！", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "加载 CSV 文件失败！", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPhoneNumbersFromExcel(filePath: String) {
        try {
            val inputStream = FileInputStream(File(filePath))
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0) // 读取第一个工作表
            for (row in sheet) {
                val cell = row.getCell(0) // 假设电话号码在第一列
                phoneNumbers.add(cell.toString().trim())
            }
            workbook.close()
            inputStream.close()
            Toast.makeText(this, "成功加载 ${phoneNumbers.size} 个电话号码！", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "加载 Excel 文件失败！", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAutoDialing() {
        currentIndex = 0 // 重置索引
        dialNextNumber()
    }

    private fun dialNextNumber() {
        if (isStopped) {
            return // 停止拨打流程
        }

        if (currentIndex >= phoneNumbers.size) {
            Toast.makeText(this, "所有电话号码已拨打完成！", Toast.LENGTH_SHORT).show()
            return
        }

        if (isPaused) {
            handler.postDelayed({ dialNextNumber() }, 1000) // 每秒检查是否恢复
            return
        }

        val phoneNumber = phoneNumbers[currentIndex]
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startActivity(intent)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
        }

        handler.postDelayed({
            currentIndex++
            dialNextNumber()
        }, 5000) // 延迟 5 秒
    }
}