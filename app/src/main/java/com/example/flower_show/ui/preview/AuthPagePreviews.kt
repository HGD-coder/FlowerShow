package com.example.flower_show.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.flower_show.data.auth.AuthField
import com.example.flower_show.ui.screen.LoginScreen
import com.example.flower_show.ui.screen.RegisterScreen
import com.example.flower_show.ui.theme.FlowerShowTheme
import com.example.flower_show.viewmodel.AuthMode
import com.example.flower_show.viewmodel.AuthState

@Preview(name = "登录", showBackground = true, backgroundColor = 0xFF081120)
@Composable
private fun LoginPreview() {
    FlowerShowTheme {
        LoginScreen(
            state = AuthState(isInitializing = false),
            onIntent = {},
            onBack = {},
        )
    }
}

@Preview(name = "注册错误", showBackground = true, backgroundColor = 0xFF081120)
@Composable
private fun RegisterErrorPreview() {
    FlowerShowTheme {
        RegisterScreen(
            state = AuthState(
                mode = AuthMode.Register,
                username = "garden user",
                nickname = "花园用户",
                isInitializing = false,
                fieldErrors = mapOf(
                    AuthField.Username to "仅支持英文字母、数字、点、下划线和短横线",
                ),
                message = "请检查填写内容是否符合要求",
            ),
            onIntent = {},
            onBack = {},
        )
    }
}

@Preview(name = "注册提交中", showBackground = true, backgroundColor = 0xFF081120)
@Composable
private fun RegisterLoadingPreview() {
    FlowerShowTheme {
        RegisterScreen(
            state = AuthState(
                mode = AuthMode.Register,
                username = "garden.user",
                nickname = "花园用户",
                bio = "阳台种花",
                isInitializing = false,
                isSubmitting = true,
            ),
            onIntent = {},
            onBack = {},
        )
    }
}
