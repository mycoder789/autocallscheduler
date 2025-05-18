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
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.rezwan.autocallscheduler.R
import com.rezwan.autocallscheduler.callbacks.TimerTaskListener
import com.rezwan.autocallscheduler.constants.const
import com.rezwan.autocallscheduler.ext.error
import com.rezwan.autocallscheduler.ext.info
import com.rezwan.autocallscheduler.helper.RTimer
import com.rezwan.autocallscheduler.utils.StringUtils
import com.rezwan.autocallscheduler.viewmodel.AppViewModel
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*

class CallerActivity : BaseActivity(), TimerTaskListener {
    private val CALL_PHONE = Manifest.permission.CALL_PHONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initListeners()
        setupObservers()
    }

    private fun initListeners() {
        btnConfirm.setOnClickListener {
            edtNumber.text?.let { input ->
                if (input.isNotEmpty()) {
                    startActivity(Intent(this, ScheduleActivity::class.java))
                } else {
                    showToast("Please enter your number")
                }
            }
        }

        btnCall.setOnClickListener {
            edtNumber.text?.let { input ->
                if (input.isNotEmpty()) {
                    startAutoCall()
                } else {
                    showToast("Please enter your number")
                }
            }
        }

        btnClear.setOnClickListener {
            clearInputFields()
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun setupObservers() {
        viewModel.permissionObservable.observe(this, Observer {
            when (it) {
                is AppViewModel.AppPermissions.NO_PERMISSION_REQUIRED -> {
                    directPhoneCall()
                }
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
        })

        viewModel.observeScheduler(this, Observer {
            handleScheduledCall(it)
        })
    }

    private fun handleScheduledCall(schedule: AppViewModel.Scheduler) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm aa")
            val date1 = Date()
            val date2 = sdf.parse("${schedule.year}-${schedule.month}-${schedule.day} ${schedule.hour}:${schedule.min} ${schedule.format}")
            error(this, "date1 $date1    ,    date2 $date2")

            when {
                date1.after(date2) -> {
                    info("app", "Date1 is after Date2")
                    startAutoCall()
                }
                date1.before(date2) -> {
                    info("app", "Date1 is before Date2")
                    val duration: Long = date2.time - date1.time
                    startTimerDurationCall(duration)
                    btnConfirm.visibility = View.INVISIBLE
                }
                else -> {
                    info("app", "Date1 is equal to Date2")
                    startAutoCall()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startAutoCall() {
        if (Build.VERSION.SDK_INT < 23) {
            viewModel.permissionObservable.value = AppViewModel.AppPermissions.NO_PERMISSION_REQUIRED()
        } else {
            checkPermission(CALL_PHONE)
        }
    }

    private fun startTimerDurationCall(duration: Long) {
        RTimer(duration, 1000).apply {
            start()
            setListener(this@CallerActivity)
        }
    }

    @SuppressLint("MissingPermission")
    private fun directPhoneCall() {
        val phoneNo = getPhoneNoFromFields()
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNo")))
    }

    private fun getPhoneNoFromFields() = "${editText2.text}${edtNumber.text}${editText4.text}"

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

    private fun clearInputFields() {
        editText2.text?.clear()
        edtNumber.text?.clear()
        editText4.text?.clear()
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

    override fun onTimerFinished() {
        btnConfirm.visibility = View.VISIBLE
        tvTimer.text = "0:00"
        startAutoCall()
    }

    override fun onTimerTicked(remainingTime: String) {
        runOnUiThread {
            tvTimer.text = StringUtils.timeStringToSpan(remainingTime)
        }
    }
}