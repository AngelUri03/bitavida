package com.bitavida.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import com.bitavida.app.domain.validation.CaptureLimit
import java.text.NumberFormat

@Composable
internal fun BitavidaLimitedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    limit: CaptureLimit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    shape: Shape = MaterialTheme.shapes.extraSmall,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    enabled: Boolean = true,
    singleLine: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    showCounter: Boolean? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = LocalTextStyle.current,
    additionalInputValidation: (String) -> Boolean = { true },
) {
    var rejected by remember { mutableStateOf(false) }
    val count = remember(value, limit) { limit.characterCount(value) }
    val counterVisible = showCounter ?: (limit.unicodeText || rejected || isError || count >= limit.maximum * 9 / 10)
    val locale = LocalLocale.current.platformLocale
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val effectiveMaxLines = if (limit.unicodeText && maxLines == Int.MAX_VALUE) maxOf(minLines, 6) else maxLines
    OutlinedTextField(
        value = value,
        onValueChange = { proposed ->
            if (limit.acceptsEdit(value, proposed) && additionalInputValidation(proposed)) {
                rejected = false
                onValueChange(proposed)
            } else if (!limit.unicodeText) {
                // Preserve the pre-existing capture behavior of modules outside this change.
                rejected = true
            }
        },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        isError = isError || (!limit.unicodeText && rejected),
        suffix = suffix,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = textStyle,
        minLines = minLines,
        maxLines = effectiveMaxLines,
        singleLine = singleLine,
        enabled = enabled,
        trailingIcon = trailingIcon,
        leadingIcon = leadingIcon,
        shape = shape,
        colors = colors,
        supportingText = if (counterVisible || supportingText != null) {
            {
                Column {
                    if (!limit.unicodeText && (rejected || (isError && count > limit.maximum))) {
                        Text(limit.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    } else {
                        supportingText?.invoke()
                    }
                    if (counterVisible) {
                        if (limit.unicodeText) Text(
                            "${numbers.format(count)} / ${numbers.format(limit.maximum)}",
                            Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ) else Text(
                            "$count/${limit.maximum}", Modifier.fillMaxWidth(), textAlign = TextAlign.End,
                        )
                    }
                }
            }
        } else null,
    )
}
