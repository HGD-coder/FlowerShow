package com.example.flower_show.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.flower_show.data.auth.AuthApiException
import com.example.flower_show.data.auth.AuthField
import com.example.flower_show.data.auth.AuthRepository
import com.example.flower_show.data.auth.AuthSessionManager
import com.example.flower_show.data.auth.AuthValidator
import com.example.flower_show.data.auth.LoginRequest
import com.example.flower_show.data.auth.RegisterRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    private val sessionManager: AuthSessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.currentUser.collect { user ->
                _state.update { it.copy(currentUser = user) }
            }
        }
        viewModelScope.launch {
            // The repository performs token-store I/O on its IO dispatcher. Always asking it to
            // restore also keeps Keystore access off the main thread when no token is present.
            val restoreResult = repository.restoreSession()
            _state.update { current ->
                current.copy(
                    isInitializing = false,
                    message = restoreResult.exceptionOrNull()
                        ?.takeUnless { it is AuthApiException && it.statusCode == 401 }
                        ?.let { "暂时无法恢复登录，请重新登录" },
                )
            }
        }
    }

    fun dispatch(intent: AuthIntent) {
        when (intent) {
            AuthIntent.ShowLogin -> switchMode(AuthMode.Login)
            AuthIntent.ShowRegister -> switchMode(AuthMode.Register)
            is AuthIntent.UsernameChanged -> updateField(AuthField.Username, intent.value)
            is AuthIntent.PasswordChanged -> updateField(AuthField.Password, intent.value)
            is AuthIntent.NicknameChanged -> updateField(AuthField.Nickname, intent.value)
            is AuthIntent.AvatarUrlChanged -> updateField(AuthField.AvatarUrl, intent.value)
            is AuthIntent.BioChanged -> updateField(AuthField.Bio, intent.value)
            AuthIntent.TogglePasswordVisibility ->
                _state.update { it.copy(passwordVisible = !it.passwordVisible) }
            AuthIntent.Submit -> submit()
            AuthIntent.DismissMessage -> _state.update { it.copy(message = null) }
            AuthIntent.Logout -> logout(allDevices = false)
            AuthIntent.LogoutAll -> logout(allDevices = true)
        }
    }

    private fun switchMode(mode: AuthMode) {
        _state.update {
            it.copy(
                mode = mode,
                password = "",
                passwordVisible = false,
                fieldErrors = emptyMap(),
                message = null,
                // 允许在提交进行中切换模式：旧请求的结果会被丢弃（见 submit），
                // 若不复位标志，新模式下会被 isSubmitting 挡住到旧请求结束。
                isSubmitting = false,
            )
        }
    }

    private fun updateField(field: AuthField, value: String) {
        _state.update { current ->
            val updated = when (field) {
                AuthField.Username -> current.copy(username = value)
                AuthField.Password -> current.copy(password = value)
                AuthField.Nickname -> current.copy(nickname = value)
                AuthField.AvatarUrl -> current.copy(avatarUrl = value)
                AuthField.Bio -> current.copy(bio = value)
            }
            updated.copy(
                fieldErrors = updated.fieldErrors - field,
                message = null,
            )
        }
    }

    private fun submit() {
        val snapshot = _state.value
        if (snapshot.isSubmitting) return
        val validation = if (snapshot.mode == AuthMode.Login) {
            AuthValidator.validateLogin(snapshot.username, snapshot.password)
        } else {
            AuthValidator.validateRegistration(
                username = snapshot.username,
                password = snapshot.password,
                nickname = snapshot.nickname,
                bio = snapshot.bio,
            )
        }
        if (!validation.isValid) {
            _state.update { it.copy(fieldErrors = validation.fieldErrors, message = null) }
            return
        }

        _state.update { it.copy(isSubmitting = true, fieldErrors = emptyMap(), message = null) }
        viewModelScope.launch {
            val result = if (snapshot.mode == AuthMode.Login) {
                repository.login(
                    LoginRequest(
                        username = AuthValidator.normalizeUsername(snapshot.username),
                        password = snapshot.password,
                    ),
                )
            } else {
                repository.register(
                    RegisterRequest(
                        username = AuthValidator.normalizeUsername(snapshot.username),
                        password = snapshot.password,
                        nickname = snapshot.nickname.trim(),
                        avatarUrl = AuthValidator.optionalText(snapshot.avatarUrl),
                        bio = AuthValidator.optionalText(snapshot.bio),
                    ),
                )
            }
            // 提交期间用户切换了登录/注册模式：丢弃旧请求的结果，
            // 否则旧请求失败会清掉用户在新表单里输入的密码。
            if (_state.value.mode != snapshot.mode) return@launch
            result.fold(
                onSuccess = {
                    _state.update { current ->
                        current.copy(
                            username = AuthValidator.normalizeUsername(current.username),
                            password = "",
                            isSubmitting = false,
                            fieldErrors = emptyMap(),
                            message = null,
                        )
                    }
                },
                onFailure = ::handleFailure,
            )
        }
    }

    private fun logout(allDevices: Boolean) {
        if (_state.value.isSubmitting) return
        _state.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            val result = if (allDevices) repository.logoutAll() else repository.logout()
            _state.update {
                it.copy(
                    password = "",
                    isSubmitting = false,
                    message = result.exceptionOrNull()?.let { "已清除本地登录状态，服务端退出失败" },
                )
            }
        }
    }

    private fun handleFailure(error: Throwable) {
        val apiError = error as? AuthApiException
        val message = when (apiError?.statusCode) {
            400 -> "请检查填写内容是否符合要求"
            401 -> "用户名或密码错误"
            403 -> "账号不可用或身份不匹配"
            409 -> "用户名或同名账号已存在"
            429 -> "连续失败过多，账号已锁定 15 分钟"
            else -> "网络连接失败，请稍后重试"
        }
        val fieldErrors = if (apiError?.statusCode == 409) {
            mapOf(AuthField.Username to message)
        } else {
            emptyMap()
        }
        _state.update {
            it.copy(
                password = "",
                isSubmitting = false,
                fieldErrors = fieldErrors,
                message = message,
            )
        }
    }

    class Factory(
        private val repository: AuthRepository,
        private val sessionManager: AuthSessionManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java))
            return AuthViewModel(repository, sessionManager) as T
        }
    }
}
