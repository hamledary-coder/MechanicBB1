package com.workshop.mechanicbb1.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.workshop.mechanicbb1.ui.home.HomeActivity
import com.workshop.mechanicbb1.R
import com.workshop.mechanicbb1.data.remote.Config
import com.workshop.mechanicbb1.utils.UserCache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private val loginUrl = Config.Endpoints.LOGIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // مقداردهی اولیه UserCache
        UserCache.init(this)

        // چک کردن لاگین قبلی
        val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
        if (sharedPref.contains(Config.PrefKeys.PERSONNEL_ID)) {
            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val etId = findViewById<EditText>(R.id.etPersonnelID)
        val etPass = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val progress = findViewById<ProgressBar>(R.id.loginProgress)

        btnLogin.setOnClickListener {
            val pId = etId.text.toString().trim()
            val pass = etPass.text.toString().trim()

            if (pId.isNotEmpty() && pass.isNotEmpty()) {
                progress.visibility = View.VISIBLE
                btnLogin.isEnabled = false
                sendLoginRequest(pId, pass, btnLogin, progress)
            } else {
                Toast.makeText(this, "لطفاً فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendLoginRequest(pId: String, pass: String, btn: Button, pg: ProgressBar) {
        val client = OkHttpClient()
        val json = JSONObject()
        json.put("action", "login")
        json.put("personnelId", pId)
        json.put("password", pass)

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(loginUrl).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    pg.visibility = View.GONE
                    btn.isEnabled = true
                    Toast.makeText(this@LoginActivity, "خطا در اتصال به شبکه", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                val code = response.code

                Log.d("LOGIN_TEST", "HTTP Code: $code")
                Log.d("LOGIN_TEST", "Raw Response: $resBody")
                Log.d("LOGIN_URL", "Login URL: $loginUrl")
                Log.d("LOGIN_JSON", "Sending: $json")

                try {
                    val jsonRes = JSONObject(resBody ?: "{}")
                    runOnUiThread {
                        pg.visibility = View.GONE
                        btn.isEnabled = true

                        if (jsonRes.optString("status") == "success") {
                            val name = jsonRes.optString("name", "کاربر")
                            val role = jsonRes.optString("role", "3")
                            val rowId = jsonRes.optString("rowId", "")
                            val userId = jsonRes.optString("rowId", pId)

                            val sharedPref = getSharedPreferences(Config.PrefKeys.USER_PREFS, MODE_PRIVATE)
                            sharedPref.edit()
                                .putString(Config.PrefKeys.PERSONNEL_ID, pId)
                                .putString(Config.PrefKeys.USER_ROW_ID, rowId)
                                .putString(Config.PrefKeys.USERNAME, name)
                                .putString(Config.PrefKeys.USER_ROLE, role)
                                .commit()

                            // ذخیره نام کاربر فعلی در UserCache
                            UserCache.addUser(userId, name)

                            // بارگذاری همه کاربران در پس‌زمینه
                            UserCache.loadAllUsers {
                                Log.d("UserCache", "All users loaded successfully")
                                UserCache.printCache()
                            }

                            Toast.makeText(this@LoginActivity, "خوش آمدید $name", Toast.LENGTH_LONG).show()

                            val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "آیدی یا رمز اشتباه است", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        pg.visibility = View.GONE
                        btn.isEnabled = true
                        Toast.makeText(this@LoginActivity, "خطا در تحلیل داده‌های سرور", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}