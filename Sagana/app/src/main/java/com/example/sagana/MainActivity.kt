package com.example.sagana

import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var editEmail: EditText
    private lateinit var editPassword: EditText
    private lateinit var buttonTogglePassword: ImageButton

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        editEmail = findViewById(R.id.editEmail)
        editPassword = findViewById(R.id.editPassword)
        buttonTogglePassword = findViewById(R.id.buttonTogglePassword)

        buttonTogglePassword.setOnClickListener { togglePasswordVisibility() }

        editPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }

        findViewById<Button>(R.id.buttonLogin).setOnClickListener { attemptLogin() }

        findViewById<Button>(R.id.buttonDemo).setOnClickListener {
            editEmail.setText(DEMO_EMAIL)
            editPassword.setText(DEMO_PASSWORD)
            attemptLogin()
        }

        findViewById<TextView>(R.id.textForgotPassword).setOnClickListener { showComingSoon() }
        findViewById<TextView>(R.id.textCreateAccount).setOnClickListener { showComingSoon() }
    }

    private fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
        editPassword.inputType = if (isPasswordVisible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        // Changing inputType resets the typeface and cursor, so restore both.
        editPassword.typeface = editEmail.typeface
        editPassword.setSelection(editPassword.text.length)

        buttonTogglePassword.setImageResource(
            if (isPasswordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
        )
        buttonTogglePassword.contentDescription = getString(
            if (isPasswordVisible) R.string.cd_hide_password else R.string.cd_show_password
        )
    }

    private fun attemptLogin() {
        val email = editEmail.text.toString().trim()
        val password = editPassword.text.toString()

        editEmail.error = null
        editPassword.error = null

        when {
            email.isEmpty() -> {
                editEmail.error = getString(R.string.error_email_required)
                editEmail.requestFocus()
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                editEmail.error = getString(R.string.error_email_invalid)
                editEmail.requestFocus()
            }
            password.isEmpty() -> {
                editPassword.error = getString(R.string.error_password_required)
                editPassword.requestFocus()
            }
            else -> {
                val name = email.substringBefore('@')
                Toast.makeText(
                    this,
                    getString(R.string.toast_login_success, name),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showComingSoon() {
        Toast.makeText(this, R.string.toast_coming_soon, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val DEMO_EMAIL = "juan@email.com"
        private const val DEMO_PASSWORD = "demo1234"
    }
}
