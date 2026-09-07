package com.example.flower_show.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flower_show.data.auth.AuthField
import com.example.flower_show.ui.theme.ArcticColors
import com.example.flower_show.ui.theme.AuroraGradient
import com.example.flower_show.ui.theme.AuroraShapes
import com.example.flower_show.ui.theme.auroraGradient
import com.example.flower_show.viewmodel.AuthIntent
import com.example.flower_show.viewmodel.AuthState

@Composable
fun AuthLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("auth_loading_screen"),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun LoginScreen(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthScaffold(
        title = "欢迎回来",
        subtitle = "登录后继续查看朋友动态和管理你的作品",
        testTag = "login_screen",
        onBack = onBack,
        modifier = modifier,
    ) {
        UsernameField(
            value = state.username,
            error = state.fieldErrors[AuthField.Username],
            enabled = !state.isSubmitting,
            onValueChange = { onIntent(AuthIntent.UsernameChanged(it)) },
        )
        PasswordField(
            value = state.password,
            error = state.fieldErrors[AuthField.Password],
            visible = state.passwordVisible,
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Done,
            onValueChange = { onIntent(AuthIntent.PasswordChanged(it)) },
            onToggleVisibility = { onIntent(AuthIntent.TogglePasswordVisibility) },
            onDone = { onIntent(AuthIntent.Submit) },
        )
        AuthMessage(state.message)
        SubmitButton(
            text = "登录",
            submittingText = "正在登录",
            isSubmitting = state.isSubmitting,
            onClick = { onIntent(AuthIntent.Submit) },
        )
        TextButton(
            onClick = { onIntent(AuthIntent.ShowRegister) },
            enabled = !state.isSubmitting,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("auth_switch_register"),
        ) {
            Text("还没有账号？注册")
        }
    }
}

@Composable
fun RegisterScreen(
    state: AuthState,
    onIntent: (AuthIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthScaffold(
        title = "创建账号",
        subtitle = "注册成功后会自动登录",
        testTag = "register_screen",
        onBack = onBack,
        modifier = modifier,
    ) {
        UsernameField(
            value = state.username,
            error = state.fieldErrors[AuthField.Username],
            enabled = !state.isSubmitting,
            onValueChange = { onIntent(AuthIntent.UsernameChanged(it)) },
        )
        PasswordField(
            value = state.password,
            error = state.fieldErrors[AuthField.Password],
            visible = state.passwordVisible,
            enabled = !state.isSubmitting,
            imeAction = ImeAction.Next,
            onValueChange = { onIntent(AuthIntent.PasswordChanged(it)) },
            onToggleVisibility = { onIntent(AuthIntent.TogglePasswordVisibility) },
        )
        AuthTextField(
            value = state.nickname,
            onValueChange = { onIntent(AuthIntent.NicknameChanged(it)) },
            label = "昵称",
            testTag = "auth_nickname",
            error = state.fieldErrors[AuthField.Nickname],
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            supportingHint = "必填，最多 80 个字符",
        )
        AuthTextField(
            value = state.avatarUrl,
            onValueChange = { onIntent(AuthIntent.AvatarUrlChanged(it)) },
            label = "头像链接（可选）",
            testTag = "auth_avatar_url",
            error = state.fieldErrors[AuthField.AvatarUrl],
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
            ),
        )
        AuthTextField(
            value = state.bio,
            onValueChange = { onIntent(AuthIntent.BioChanged(it)) },
            label = "简介（可选）",
            testTag = "auth_bio",
            error = state.fieldErrors[AuthField.Bio],
            enabled = !state.isSubmitting,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            supportingHint = "最多 255 个字符",
            singleLine = false,
            minLines = 3,
        )
        AuthMessage(state.message)
        SubmitButton(
            text = "注册并登录",
            submittingText = "正在注册",
            isSubmitting = state.isSubmitting,
            onClick = { onIntent(AuthIntent.Submit) },
        )
        TextButton(
            onClick = { onIntent(AuthIntent.ShowLogin) },
            enabled = !state.isSubmitting,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("auth_switch_login"),
        ) {
            Text("已有账号？登录")
        }
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    testTag: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.testTag(testTag),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .testTag("auth_scroll_content")
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = content,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UsernameField(
    value: String,
    error: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    AuthTextField(
        value = value,
        onValueChange = onValueChange,
        label = "用户名",
        testTag = "auth_username",
        error = error,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next,
        ),
        supportingHint = "3-32 位，仅限字母、数字、.、_、-",
    )
}

@Composable
private fun PasswordField(
    value: String,
    error: String?,
    visible: Boolean,
    enabled: Boolean,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onDone: () -> Unit = {},
) {
    AuroraAuthTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_password"),
        enabled = enabled,
        label = "密码",
        errorMessage = error,
        supportingHint = "8-72 个字符，且不超过 72 个 UTF-8 字节",
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility, enabled = enabled) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "隐藏密码" else "显示密码",
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() },
        ),
    )
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    error: String?,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    supportingHint: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    AuroraAuthTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        enabled = enabled,
        label = label,
        errorMessage = error,
        supportingHint = supportingHint,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuroraAuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    supportingHint: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val isError = errorMessage != null
    val colors = OutlinedTextFieldDefaults.colors()
    val supportingText = errorMessage ?: supportingHint
    val textColor = when {
        !enabled -> colors.disabledTextColor
        isError -> colors.errorTextColor
        focused -> colors.focusedTextColor
        else -> colors.unfocusedTextColor
    }
    val labelTopPadding = with(LocalDensity.current) { 8.sp.toDp() }

    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .semantics(mergeDescendants = true) {
                    if (errorMessage != null) error(errorMessage)
                }
                .padding(top = labelTopPadding)
                .defaultMinSize(
                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                    minHeight = OutlinedTextFieldDefaults.MinHeight,
                ),
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            cursorBrush = SolidColor(
                if (isError) colors.errorCursorColor else colors.cursorColor,
            ),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            minLines = minLines,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    interactionSource = interactionSource,
                    isError = isError,
                    label = { Text(label) },
                    trailingIcon = trailingIcon,
                    supportingText = supportingText?.let { message ->
                        { Text(message) }
                    },
                    colors = colors,
                    container = {
                        if (enabled && focused && !isError) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, AuroraGradient, AuroraShapes.Capsule),
                            )
                        } else {
                            OutlinedTextFieldDefaults.Container(
                                enabled = enabled,
                                isError = isError,
                                interactionSource = interactionSource,
                                colors = colors,
                                shape = AuroraShapes.Capsule,
                            )
                        }
                    },
                )
            },
        )
    }
}

@Composable
private fun AuthMessage(message: String?) {
    if (message == null) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_message")
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SubmitButton(
    text: String,
    submittingText: String,
    isSubmitting: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .blur(
                    radius = 24.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .background(ArcticColors.AuroraCyan.copy(alpha = 0.35f), AuroraShapes.Capsule),
        )
        Button(
            onClick = onClick,
            enabled = !isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    brush = auroraGradient(alpha = if (isSubmitting) 0.55f else 1f),
                    shape = AuroraShapes.Capsule,
                )
                .testTag("auth_submit"),
            shape = AuroraShapes.Capsule,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                contentColor = ArcticColors.TextPrimary,
                disabledContentColor = ArcticColors.TextPrimary.copy(alpha = 0.72f),
            ),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(20.dp),
                    color = ArcticColors.TextPrimary,
                    strokeWidth = 2.dp,
                )
            }
            Text(if (isSubmitting) submittingText else text)
        }
    }
}
