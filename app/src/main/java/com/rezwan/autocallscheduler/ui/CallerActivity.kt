/*
 *       Copyright (c) 2020. RRsaikat
 *
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *       You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 */

package com.rezwan.autocallscheduler.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.rezwan.autocallscheduler.R
import com.rezwan.autocallscheduler.constants.const
import com.rezwan.autocallscheduler.viewmodel.AppViewModel
import kotlinx.android.synthetic.main.activity_main.*

class CallerActivity : BaseActivity() {

    private val CALL_PHONE = Manifest.permission.CALL_PHONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initListeners()
        setupObservers()
    }

    private fun initListeners() {
        // 保留调度页面跳转功能
        btnConfirm.setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
    }

    private fun setupObservers() {
        viewModel.permissionObservable.observe(this) {
            when (it) {
                is AppViewModel.AppPermissions.NO_PERMISSION_REQUIRED,
                is AppViewModel.AppPermissions.GRANTED -> {
                    directPhoneCall()
                }
                is AppViewModel.AppPermissions.NOT_GRANTED -> {
                    showToast("Permission is required")
                }
                is AppViewModel.AppPermissions.SHOW_RATIONALE -> {
                    // Handle rationale display if needed
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun directPhoneCall() {
        val phoneNo = getPhoneNoFromFields()
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNo")))
    }

    // 从号码导入逻辑中获取电话号码
    private fun getPhoneNoFromFields() = "PREDEFINED_PHONE_NUMBER" // 替换为实际逻辑

    private fun checkPermission(permission: String) {
        if (hasPermission(permission)) {
            viewModel.permissionObservable.value = AppViewModel.AppPermissions.GRANTED()
        } else {
            requestPermission(permission)
        }
    }

    private fun requestPermission(permission: String) {
        ActivityCompat.requestPermissions(
            this, arrayOf(permission),
            const.ESSENTIAL_PERMISSIONS_REQUEST_CODE
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.call_menu, menu)
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        checkPermissionsRequestResult(requestCode, grantResults)
    }

    private fun checkPermissionsRequestResult(
        requestCode: Int,
        grantResults: IntArray
    ): AppViewModel.AppPermissions {
        return when {
            requestCode != const.ESSENTIAL_PERMISSIONS_REQUEST_CODE -> AppViewModel.AppPermissions.NOT_GRANTED()
            grantResults.none { it == PackageManager.PERMISSION_DENIED } -> AppViewModel.AppPermissions.GRANTED()
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CALL_PHONE) -> AppViewModel.AppPermissions.SHOW_RATIONALE()
            else -> AppViewModel.AppPermissions.NOT_GRANTED()
        }
    }
}