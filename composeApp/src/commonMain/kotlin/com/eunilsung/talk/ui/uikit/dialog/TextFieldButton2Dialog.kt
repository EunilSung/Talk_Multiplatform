package com.eunilsung.talk.ui.uikit.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.alarm
import multiplatformtalk.composeapp.generated.resources.cancel
import multiplatformtalk.composeapp.generated.resources.ok
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.button.ButtonV1
import com.eunilsung.talk.ui.uikit.line.LineDivider
import org.jetbrains.compose.resources.stringResource

@Composable
fun TextFieldButton2Dialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit = { Text(stringResource(Res.string.alarm)) },
    initialValue: String = "",
    hintTxt: String = "",
    confirmText: String = stringResource(Res.string.ok),
    dismissText: String? = stringResource(Res.string.cancel),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit = onDismissRequest
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var text by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(initialValue.length)
            )
        )
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = onDismissRequest
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            shape = RoundedCornerShape(8.dp),
            color = AppColors.BgSub
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                title()

                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 40.dp)
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(AppColors.Main),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            defaultKeyboardAction(ImeAction.Done)
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier.wrapContentHeight()
                        ) {
                            if (text.text.isEmpty()) {
                                Text(
                                    text = hintTxt,
                                    fontSize = 16.sp,
                                    color = AppColors.TextHint
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                LineDivider(color = AppColors.Main)

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (dismissText != null) {
                        ButtonV1(
                            modifier = Modifier.weight(1f),
                            text = dismissText,
                            containerColor = AppColors.CancelButton,
                            contentColor = AppColors.Black,
                            onClick = onDismiss
                        )
                    }

                    ButtonV1(
                        modifier = Modifier.weight(1f),
                        text = confirmText,
                        onClick = { onConfirm(text.text) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TextFieldButton2DialogPreview() {
    MaterialTheme {
        TextFieldButton2Dialog(
            onDismissRequest = {},
            title = { Text("Title") },
            initialValue = "Placeholder",
            hintTxt = "Hint",
            onConfirm = { _ -> }
        )
    }
}