package com.example.texteditor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PREFS_NAME = "editor_draft_prefs"
private const val KEY_TITLE = "draft_title"
private const val KEY_CONTENT = "draft_content"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichTextEditorScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // 1. 初始化时从本地恢复草稿，防止杀进程丢失
    var title by remember { mutableStateOf(prefs.getString(KEY_TITLE, "") ?: "") }
    var editorValue by remember {
        val savedContent = prefs.getString(KEY_CONTENT, "") ?: ""
        mutableStateOf(TextFieldValue(savedContent, selection = TextRange(savedContent.length)))
    }

    var isPreviewMode by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var saveStatusText by remember { mutableStateOf("已本地同步") }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 2. 防抖自动保存（停止输入 500ms 后写入本地存储）
    LaunchedEffect(title, editorValue.text) {
        saveStatusText = "保存中..."
        delay(500L)
        prefs.edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_CONTENT, editorValue.text)
            .apply()
        saveStatusText = "已自动保存"
    }

    // 3. 精确字数统计：过滤所有空白符（空格、换行、制表符）
    val wordCount = remember(editorValue.text) {
        editorValue.text.count { !it.isWhitespace() }
    }

    // 清空二次确认弹窗
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("确认清空内容？") },
            text = { Text("清空后当前标题与正文将被移除（可在底部提示栏短暂撤销）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val backupTitle = title
                        val backupEditor = editorValue
                        title = ""
                        editorValue = TextFieldValue("")
                        showClearDialog = false

                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "已清空全部内容",
                                actionLabel = "撤销",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                title = backupTitle
                                editorValue = backupEditor
                            }
                        }
                    }
                ) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Markdown 轻量编辑器", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            text = "$wordCount 字 · $saveStatusText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // 编辑 / 渲染预览模式切换
                    IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                        Icon(
                            imageVector = if (isPreviewMode) Icons.Default.Edit else Icons.Default.Visibility,
                            contentDescription = if (isPreviewMode) "切回编辑" else "预览效果"
                        )
                    }
                    // 复制全文（使用原生 ClipboardManager 消除弃用警告）
                    IconButton(onClick = {
                        val fullText = if (title.isNotBlank()) "$title\n\n${editorValue.text}" else editorValue.text
                        copyToSystemClipboard(context, "文章内容", fullText)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("已复制全文到剪贴板")
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制全文")
                    }
                    // 清空按钮（触发防呆确认）
                    IconButton(
                        enabled = title.isNotEmpty() || editorValue.text.isNotEmpty(),
                        onClick = { showClearDialog = true }
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "清空")
                    }
                }
            )
        },
        bottomBar = {
            // 仅在编辑模式下显示格式工具栏
            if (!isPreviewMode) {
                EditorFormatToolbar(
                    onInsertMarkdown = { prefix, suffix ->
                        editorValue = wrapSelectionWithSyntax(editorValue, prefix, suffix)
                    },
                    onToggleBullet = {
                        editorValue = toggleLinePrefix(editorValue, "• ")
                    },
                    onToggleHeading = {
                        editorValue = toggleLinePrefix(editorValue, "# ")
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            if (isPreviewMode) {
                // 渲染预览模式（所见即所得视图）
                Text(
                    text = title.ifBlank { "无标题文档" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Text(
                    text = parseBasicMarkdown(editorValue.text.ifBlank { "暂无正文内容" }),
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                // 源码编辑模式：标题强制单行并过滤换行符
                BasicTextField(
                    value = title,
                    onValueChange = { input ->
                        title = input.replace("\n", "").replace("\r", "")
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) {
                            Text(
                                text = "输入文章标题（单行）...",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 正文编辑区
                BasicTextField(
                    value = editorValue,
                    onValueChange = { editorValue = it },
                    textStyle = TextStyle(
                        fontSize = 17.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (editorValue.text.isEmpty()) {
                            Text(
                                text = "支持 Markdown 语法（点击右上角眼睛图标可实时预览排版）...",
                                fontSize = 17.sp,
                                color = Color.Gray.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 400.dp)
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun EditorFormatToolbar(
    onInsertMarkdown: (prefix: String, suffix: String) -> Unit,
    onToggleBullet: () -> Unit,
    onToggleHeading: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolbarChip(label = "H1 标题", onClick = onToggleHeading)
            ToolbarChip(label = "B 加粗", onClick = { onInsertMarkdown("**", "**") })
            ToolbarChip(label = "I 斜体", onClick = { onInsertMarkdown("*", "*") })
            ToolbarChip(label = "S 删除线", onClick = { onInsertMarkdown("~~", "~~") })
            ToolbarChip(label = "• 列表", onClick = onToggleBullet)
            ToolbarChip(label = "</> 代码", onClick = { onInsertMarkdown("`", "`") })
        }
    }
}

@Composable
private fun ToolbarChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// 现代系统剪贴板写入（无弃用警告）
private fun copyToSystemClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

// 选区包裹格式化标记
private fun wrapSelectionWithSyntax(
    value: TextFieldValue,
    prefix: String,
    suffix: String
): TextFieldValue {
    val text = value.text
    val start = value.selection.min
    val end = value.selection.max
    val selectedText = text.substring(start, end)

    val newText = buildString {
        append(text.substring(0, start))
        append(prefix)
        append(selectedText)
        append(suffix)
        append(text.substring(end))
    }

    val newCursorPos = if (selectedText.isEmpty()) {
        start + prefix.length
    } else {
        end + prefix.length + suffix.length
    }

    return TextFieldValue(
        text = newText,
        selection = TextRange(newCursorPos)
    )
}

// 智能切换行首标记（重复点击不再叠加 # # ，而是取消标记）
private fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val cursor = value.selection.min
    val lineStart = text.lastIndexOf('\n', startIndex = (cursor - 1).coerceAtLeast(0)) + 1
    val lineEnd = text.indexOf('\n', startIndex = lineStart).let { if (it == -1) text.length else it }
    val currentLine = text.substring(lineStart, lineEnd)

    return if (currentLine.startsWith(prefix)) {
        // 已有该前缀 -> 移除前缀
        val newText = buildString {
            append(text.substring(0, lineStart))
            append(currentLine.removePrefix(prefix))
            append(text.substring(lineEnd))
        }
        val newCursor = (cursor - prefix.length).coerceAtLeast(lineStart)
        TextFieldValue(text = newText, selection = TextRange(newCursor))
    } else {
        // 无该前缀 -> 添加前缀
        val newText = buildString {
            append(text.substring(0, lineStart))
            append(prefix)
            append(text.substring(lineStart))
        }
        TextFieldValue(text = newText, selection = TextRange(cursor + prefix.length))
    }
}

// 轻量级 Markdown 预览解析器（支持 H1、加粗、斜体、删除线）
private fun parseBasicMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    val lines = raw.split("\n")
    lines.forEachIndexed { index, line ->
        var content = line
        val isH1 = content.startsWith("# ")
        if (isH1) {
            content = content.removePrefix("# ")
            pushStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold))
        }

        // 简易行内解析（加粗 **...**、斜体 *...*、删除线 ~~...~~）
        val tokenRegex = Regex("(\*\*.*?\*\*|\*.*?\*|~~.*?~~)")
        var lastIdx = 0
        for (match in tokenRegex.findAll(content)) {
            append(content.substring(lastIdx, match.range.first))
            val token = match.value
            when {
                token.startsWith("**") && token.endsWith("**") && token.length > 4 -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.substring(2, token.length - 2))
                    }
                }
                token.startsWith("~~") && token.endsWith("~~") && token.length > 4 -> {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray)) {
                        append(token.substring(2, token.length - 2))
                    }
                }
                token.startsWith("*") && token.endsWith("*") && token.length > 2 -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.substring(1, token.length - 1))
                    }
                }
                else -> append(token)
            }
            lastIdx = match.range.last + 1
        }
        append(content.substring(lastIdx))

        if (isH1) pop()
        if (index < lines.lastIndex) append("\n")
    }
}
