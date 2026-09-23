package com.jaychoi.eattheland.feature.onboarding.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jaychoi.eattheland.core.designsystem.theme.AppTheme
import com.jaychoi.eattheland.core.model.PlayerError
import com.jaychoi.eattheland.feature.onboarding.R

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        when (uiState.step) {
            OnboardingStep.Intro -> IntroStep(onNext = { onEvent(OnboardingEvent.Next) })
            OnboardingStep.Permission -> PermissionStep(onAllow = onRequestPermissions)
            OnboardingStep.Nickname -> NicknameStep(uiState, onEvent)
        }
        uiState.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(error.toMessage(), color = MaterialTheme.colorScheme.error)
            if (error == PlayerError.Network) {
                Button(onClick = { onEvent(OnboardingEvent.Retry) }) {
                    Text(stringResource(R.string.onboarding_retry))
                }
            }
        }
    }
}

@Composable
private fun IntroStep(onNext: () -> Unit) {
    Text(
        stringResource(R.string.onboarding_intro_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.onboarding_intro_body), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.onboarding_intro_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_next))
    }
}

@Composable
private fun PermissionStep(onAllow: () -> Unit) {
    Text(
        stringResource(R.string.onboarding_permission_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.onboarding_permission_body),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.onboarding_permission_allow))
    }
}

@Composable
private fun NicknameStep(uiState: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Text(
        stringResource(R.string.onboarding_nickname_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = uiState.nickname,
        onValueChange = { onEvent(OnboardingEvent.NicknameChanged(it)) },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.onboarding_nickname_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { onEvent(OnboardingEvent.Submit) },
        enabled = uiState.isNicknameValid && !uiState.isSubmitting,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (uiState.isSubmitting) {
            CircularProgressIndicator()
        } else {
            Text(stringResource(R.string.onboarding_nickname_submit))
        }
    }
}

@Composable
private fun PlayerError.toMessage(): String = stringResource(
    when (this) {
        PlayerError.Network -> R.string.onboarding_error_network
        PlayerError.NicknameTaken -> R.string.onboarding_error_taken
        PlayerError.InvalidNickname -> R.string.onboarding_error_invalid
        is PlayerError.Unknown -> R.string.onboarding_error_unknown
    },
)

@Preview(name = "Intro")
@Composable
private fun IntroPreview() {
    AppTheme { OnboardingScreen(OnboardingUiState(), onEvent = {}, onRequestPermissions = {}) }
}

@Preview(name = "Nickname dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NicknameDarkPreview() {
    AppTheme {
        OnboardingScreen(
            OnboardingUiState(
                step = OnboardingStep.Nickname,
                nickname = "땅주인",
                isNicknameValid = true,
            ),
            onEvent = {},
            onRequestPermissions = {},
        )
    }
}
