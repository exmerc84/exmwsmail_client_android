package com.exmworkspace.exmwsmail.ui.login

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exmworkspace.exmwsmail.R
import com.exmworkspace.exmwsmail.ui.components.ExmField
import com.exmworkspace.exmwsmail.ui.theme.ExmBrand

/**
 * The hero runs under the status bar, so the system icons have to be light here even though
 * the rest of the app sits on a light background. Restored on the way out.
 */
@Composable
private fun LightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

/**
 * Mirrors the webmail's login (webmail.exmworkspace.com/login): the indigo hero panel with
 * the product pitch, then the sign-in card. The web lays those side by side on a wide
 * viewport; on a phone the same two blocks stack, hero first.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    LightStatusBarIcons()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding(),
    ) {
        HeroPanel()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            ExmField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.login_email_label),
                placeholder = "tu@correo.com",
                fill = ExmBrand.fieldFillFor,
                enabled = !state.submitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            ExmField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(R.string.login_password_label),
                placeholder = "••••••••",
                fill = ExmBrand.fieldFillFor,
                enabled = !state.submitting,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Ocultar contraseña"
                            else "Mostrar contraseña",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.captchaRequired) {
                Spacer(Modifier.height(20.dp))
                CaptchaSlider(
                    onGesture = viewModel::onCaptchaGesture,
                    enabled = !state.submitting && !state.captchaBusy,
                    solved = state.captchaSolved,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let { message ->
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            GradientButton(
                label = stringResource(R.string.login_submit),
                enabled = state.canSubmit,
                loading = state.submitting,
                onClick = viewModel::submit,
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.login_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** The indigo panel: brand mark, headline and the three capability chips. */
@Composable
private fun HeroPanel() {
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .onSizeChanged { size = it }
            .background(
                ExmBrand.heroGradient(
                    widthPx = size.width.toFloat().coerceAtLeast(1f),
                    heightPx = size.height.toFloat().coerceAtLeast(1f),
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = 28.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ExmBrand.heroCard,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MailOutline,
                            contentDescription = null,
                            tint = ExmBrand.onHero,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.brand_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = ExmBrand.onHero,
                    )
                    Text(
                        text = stringResource(R.string.brand_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = ExmBrand.onHeroMuted.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.login_hero_title),
                style = MaterialTheme.typography.displaySmall,
                color = ExmBrand.onHero,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.login_hero_body),
                style = MaterialTheme.typography.bodyMedium,
                color = ExmBrand.onHeroMuted.copy(alpha = 0.85f),
            )

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureChip(
                    icon = Icons.Default.Shield,
                    title = stringResource(R.string.feature_secure),
                    subtitle = stringResource(R.string.feature_secure_sub),
                    modifier = Modifier.weight(1f),
                )
                FeatureChip(
                    icon = Icons.Default.CloudQueue,
                    title = stringResource(R.string.feature_cloud),
                    subtitle = stringResource(R.string.feature_cloud_sub),
                    modifier = Modifier.weight(1f),
                )
                FeatureChip(
                    icon = Icons.Default.PhoneAndroid,
                    title = stringResource(R.string.feature_multi),
                    subtitle = stringResource(R.string.feature_multi_sub),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FeatureChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ExmBrand.heroCard,
        modifier = modifier.border(1.dp, ExmBrand.heroCardBorder, RoundedCornerShape(12.dp)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ExmBrand.onHeroMuted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ExmBrand.onHero,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = ExmBrand.onHeroMuted.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}

/** The webmail's primary button is a 135° indigo gradient, not a flat fill. */
@Composable
private fun GradientButton(
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(10.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    brush = if (enabled || loading) ExmBrand.buttonGradient
                    else ExmBrand.buttonDisabled,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}
