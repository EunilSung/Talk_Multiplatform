package com.eunilsung.talk.ui.uikit.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import multiplatformtalk.composeapp.generated.resources.Res
import multiplatformtalk.composeapp.generated.resources.arrow_down_icon
import multiplatformtalk.composeapp.generated.resources.clear_dark
import multiplatformtalk.composeapp.generated.resources.clear_light
import multiplatformtalk.composeapp.generated.resources.close_icon
import multiplatformtalk.composeapp.generated.resources.search_icon
import com.eunilsung.talk.domain.model.User
import com.eunilsung.talk.ui.theme.AppColors
import com.eunilsung.talk.ui.uikit.chip.ChipItem
import org.jetbrains.compose.resources.painterResource


@Composable
fun SearchBar(
    searchedUser: User? = null,
    searchTxt: String,
    hintTxt: String,
    onSearchTextChanged: (String) -> Unit,
    onClearClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSearchedUserRemoveClick: () -> Unit = {},
    conditions: List<ChipItem>? = null,
    textColor: Color = AppColors.Text,
    cursorColor: Color = AppColors.PrimaryMain,
    bgColor: Color = AppColors.TextFieldBg,
    borderColor: Color = AppColors.Line,
    hintColor: Color = AppColors.TextHint,
    fontSize: TextUnit = 13.sp,
    round: Dp = 100.dp,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, top = 5.dp, bottom = 5.dp)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .wrapContentHeight()
            .background(shape = RoundedCornerShape(round), color = bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(round)
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!conditions.isNullOrEmpty()) {
                var menuExpanded by remember { mutableStateOf(false) }
                val selected = conditions.firstOrNull { it.isSelected } ?: conditions.first()

                Box {
                    Row(
                        modifier = Modifier
                            .clickable { menuExpanded = true }
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selected.text,
                            color = textColor,
                            fontSize = fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(Res.drawable.arrow_down_icon),
                            contentDescription = "Select search condition",
                            tint = textColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(12.dp),
                        containerColor = AppColors.BgSub,
                        border = BorderStroke(1.dp, AppColors.Line),
                        shadowElevation = 4.dp,
                    ) {
                        conditions.forEach { c ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = c.text,
                                        color = if (c.isSelected) AppColors.PrimaryMain else textColor,
                                        fontSize = fontSize
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    c.onClick()
                                }
                            )
                        }
                    }
                }
            }

            if (searchedUser != null){
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = AppColors.Black,
                            shape = RoundedCornerShape(5.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = searchedUser.name,
                            color = AppColors.White,
                            fontSize = fontSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )

                        IconButton(
                            onClick = onSearchedUserRemoveClick,
                            modifier = Modifier.size(18.dp).padding(end = 3.dp)
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.close_icon),
                                contentDescription = "Clear",
                                tint = AppColors.White
                            )
                        }
                    }
                }
            }

            BasicTextField(
                value = searchTxt,
                onValueChange = onSearchTextChanged,
                modifier = Modifier
                    .weight(1f)
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .padding(start = 20.dp, end = 5.dp)
                    .defaultMinSize(minHeight = 40.dp),
                textStyle = TextStyle(fontSize = fontSize, color = textColor),
                singleLine = true,
                cursorBrush = SolidColor(cursorColor),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearchClick()
                        keyboardController?.hide()
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.wrapContentHeight()
                    ) {
                        if (searchTxt.isEmpty()) {
                            Text(
                                text = hintTxt,
                                fontSize = fontSize,
                                color = hintColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (searchTxt.isNotEmpty()) {
                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        painter = painterResource(if(isDark) Res.drawable.clear_dark else Res.drawable.clear_light),
                        contentDescription = "Clear",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.size(5.dp))
            }

            IconButton(
                onClick = {
                    onSearchClick()
                    keyboardController?.hide()
                },
                modifier = Modifier
                    .padding(end = 20.dp)
                    .size(24.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.search_icon),
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SearchBarPreview() {
    SearchBar(
        searchedUser = User(name = "성은일"),
        searchTxt = "",
        hintTxt = "hintTxt",
        onSearchTextChanged = {},
        onClearClick = {},
        onSearchClick = {}
    )
}
