package com.lifeHub.login;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.lifeHub.R;
import com.lifeHub.main.ui.MainPage;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private Button btnLogin, btnGoRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnGoRegister = findViewById(R.id.btn_go_register);

        btnLogin.setOnClickListener(v -> doLogin());
        btnGoRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        etUsername.setText("");
        etPassword.setText("");
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this,
                    getString(R.string.error_empty_username_password),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!LoginManager.hasRegisteredUser(this, username)) {
            Toast.makeText(this,
                    getString(R.string.error_username_not_registered),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setEnabled(false);
        LoginManager.AUTH_EXECUTOR.execute(() -> {
            boolean verified;
            try { verified = LoginManager.checkLogin(getApplicationContext(), username, password); }
            catch (RuntimeException error) { verified = false; }
            final boolean success = verified;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                btnLogin.setEnabled(true);
                completeLogin(username, success);
            });
        });
    }

    private void completeLogin(String username, boolean success) {
        if (!success) {
            Toast.makeText(this,
                    getString(R.string.error_username_or_password),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        LoginManager.setLoggedIn(this, true, username);

        Toast.makeText(this,
                getString(R.string.login_success),
                Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, MainPage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
