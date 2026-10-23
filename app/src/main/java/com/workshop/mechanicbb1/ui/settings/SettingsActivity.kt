@file:Suppress("DEPRECATION")

package com.workshop.mechanicbb1.ui.settings

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.ui.auth.LoginActivity
import com.workshop.mechanicbb1.utils.UserCache
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

@Suppress("DEPRECATION")
class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPref: SharedPreferences

    // ویوها
    private lateinit var toolbar: Toolbar
    private lateinit var cardChangePassword: LinearLayout  // ✅ تغییر از CardView به LinearLayout
    private lateinit var cardLogout: LinearLayout          // ✅ تغییر از CardView به LinearLayout
    private lateinit var tvUserName: TextView
    private lateinit var tvPersonnelId: TextView
    private lateinit var tvVersion: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()

        sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)

        setupToolbar()
        setupClickListeners()
        displayUserInfo()
        displayAppVersion()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        cardChangePassword = findViewById(R.id.cardChangePassword)  // ✅ الان درست کار میکنه
        cardLogout = findViewById(R.id.cardLogout)                  // ✅ الان درست کار میکنه
        tvUserName = findViewById(R.id.tvUserName)
        tvPersonnelId = findViewById(R.id.tvPersonnelId)
        tvVersion = findViewById(R.id.tvVersion)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "تنظیمات"
    }

    private fun setupClickListeners() {
        cardChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        cardLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun displayUserInfo() {
        val userName = sharedPref.getString(Config.PrefKeys.USERNAME, "کاربر") ?: "کاربر"
        val personnelId = sharedPref.getString(Config.PrefKeys.PERSONNEL_ID, "") ?: ""

        tvUserName.text = userName
        tvPersonnelId.text = "$personnelId"
    }

    private fun displayAppVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            tvVersion.text = packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            tvVersion.text = "1.0"
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<EditText>(R.id.etCurrentPassword)
        val etNewPassword = dialogView.findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.etConfirmPassword)

        AlertDialog.Builder(this)
            .setTitle("تغییر رمز عبور")
            .setView(dialogView)
            .setPositiveButton("تغییر") { _, _ ->
                val currentPass = etCurrentPassword.text.toString().trim()
                val newPass = etNewPassword.text.toString().trim()
                val confirmPass = etConfirmPassword.text.toString().trim()

                when {
                    currentPass.isEmpty() -> {
                        Toast.makeText(this, "رمز فعلی را وارد کنید", Toast.LENGTH_SHORT).show()
                    }
                    newPass.isEmpty() -> {
                        Toast.makeText(this, "رمز جدید را وارد کنید", Toast.LENGTH_SHORT).show()
                    }

                    newPass != confirmPass -> {
                        Toast.makeText(this, "رمز جدید با تکرار آن مطابقت ندارد", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        changePassword(currentPass, newPass)
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun changePassword(currentPassword: String, newPassword: String) {
        val personnelId = sharedPref.getString(Config.PrefKeys.PERSONNEL_ID, "") ?: ""

        val progressDialog: ProgressDialog = ProgressDialog(this).apply {
            setMessage("در حال تغییر رمز عبور...")
            setCancelable(false)
            show()
        }

        val url = "${Config.BASE_URL}?action=changePassword"

        val request = object : StringRequest(
            Method.POST, url,
            { response ->
                progressDialog.dismiss()
                try {
                    val jsonRes = JSONObject(response)
                    if (jsonRes.optString("status") == "success") {
                        Toast.makeText(this, "رمز عبور با موفقیت تغییر کرد", Toast.LENGTH_LONG).show()
                        AlertDialog.Builder(this)
                            .setTitle("تغییر رمز")
                            .setMessage("رمز عبور تغییر کرد. لطفاً دوباره وارد شوید.")
                            .setPositiveButton("باشه") { _, _ ->
                                logout()
                            }
                            .show()
                    } else {
                        val message = jsonRes.optString("message", "خطا در تغییر رمز")
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "خطا در پردازش پاسخ", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                progressDialog.dismiss()
                Toast.makeText(this, "خطا در اتصال به شبکه", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "changePassword"
                params["personnelId"] = personnelId
                params["currentPassword"] = currentPassword
                params["newPassword"] = newPassword
                return params
            }
        }

        Volley.newRequestQueue(this).add(request)
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("خروج از حساب")
            .setMessage("آیا از خروج از حساب خود مطمئن هستید؟")
            .setPositiveButton("خروج") { _, _ ->
                logout()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun logout() {
        sharedPref.edit().clear().apply()
        UserCache.clear()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}