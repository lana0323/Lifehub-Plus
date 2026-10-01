package com.lifeHub.login;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lifeHub.R;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etPasswordConfirm;
    private Button btnRegister;

    private ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        setTitle(R.string.register);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etPasswordConfirm = findViewById(R.id.et_password_confirm);
        btnRegister = findViewById(R.id.btn_register);

        btnRegister.setOnClickListener(v -> doRegister());

        btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());
    }

    private void doRegister() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String password2 = etPasswordConfirm.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password) || TextUtils.isEmpty(password2)) {
            Toast.makeText(this, getString(R.string.error_empty_username_password), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(password2)) {
            Toast.makeText(this, getString(R.string.error_password_not_match), Toast.LENGTH_SHORT).show();
            return;
        }

        if (LoginManager.hasRegisteredUser(this, username)) {
            Toast.makeText(this, getString(R.string.error_username_already_registered), Toast.LENGTH_SHORT).show();
            return;
        }

        btnRegister.setEnabled(false);
        LoginManager.AUTH_EXECUTOR.execute(() -> {
            boolean registered;
            try { LoginManager.register(getApplicationContext(), username, password); registered = true; }
            catch (RuntimeException error) { registered = false; }
            final boolean success = registered;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                btnRegister.setEnabled(true);
                Toast.makeText(this, success ? getString(R.string.register_success) : getString(R.string.profile_save_failed), Toast.LENGTH_SHORT).show();
                if (success) finish();
            });
        });
    }

}
