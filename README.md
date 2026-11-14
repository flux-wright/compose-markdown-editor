# Compose Markdown Editor / 极简文字编辑部

A minimal, single-screen **Markdown text editor** built with Kotlin + Jetpack Compose + Material 3.
Zero heavy third-party libraries — pure AndroidX.

一个极简的单屏 **Markdown 文字编辑器**，Kotlin + Jetpack Compose + Material 3 实现，
不依赖任何第三方重型库，纯 AndroidX 原生实现。

> Note: formatting is inserted as Markdown syntax (`**bold**`, `*italic*`, `~~strike~~`, `# `, `` `code` ``),
> not WYSIWYG rendering. What you copy out is clean Markdown source.
>
> 说明：排版以 Markdown 语法标记插入（`**加粗**`、`*斜体*`、`~~删除线~~`、`# ` 标题、`` `代码` ``），
> 不是所见即所得渲染；复制导出的就是干净的 Markdown 源码。

## Features / 功能

- Title field + body editor in one screen / 标题 + 正文一体编辑
- Format toolbar that rides above the soft keyboard: H1 (toggle), bold, italic, strikethrough, bullet list (toggle), inline code
  底部格式工具栏（随键盘顶起）：H1 标题（可开关）、加粗、斜体、删除线、无序列表（可开关）、行内代码
- **Preview mode**: one tap on the eye icon renders headings, bold, italic and strikethrough
  **预览模式**：点右上角眼睛图标，一键渲染标题、加粗、斜体、删除线效果
- **Draft autosave**: title and body are debounce-saved locally (SharedPreferences) and restored on relaunch
  **草稿自动保存**：标题与正文防抖写入本地（SharedPreferences），重启/被杀后自动恢复
- Live character count (non-whitespace) with save status / 实时字数统计（不计空白）＋保存状态显示
- Copy the whole document (title + body) to the clipboard in one tap / 一键复制全文（标题 + 正文）
- Clear with confirmation dialog + Undo snackbar / 清空带二次确认弹窗＋撤销提示条
- Smart selection wrapping: with no selection, the cursor lands between the markers
  智能选区包裹：未选中文字时，光标自动落在标记中间

## Run it / 如何运行

1. In Android Studio, create a new **Empty Activity (Jetpack Compose)** project.
   在 Android Studio 新建 **Empty Activity (Jetpack Compose)** 项目。
2. Set the application package to `com.example.texteditor` (or change the first `package` line of both files).
   包名设为 `com.example.texteditor`（或改两个文件的第一行包名保持一致）。
3. Copy `MainActivity.kt` and `RichTextEditorScreen.kt` into the project, replacing the template's `MainActivity.kt`.
   把 `MainActivity.kt`、`RichTextEditorScreen.kt` 拷进项目，替换模板自带的 `MainActivity.kt`。
4. If the copy/delete icons fail to resolve, add the extended icons dependency:
   如果复制/删除图标报红，在 `build.gradle.kts` 里补一行图标扩展库依赖：

   ```kotlin
   implementation("androidx.compose.material:material-icons-extended")
   ```

5. Run. / 运行即可。

## Files / 文件说明

- `MainActivity.kt` — entry point; hosts the editor inside `MaterialTheme`. / 入口，承载编辑界面
- `RichTextEditorScreen.kt` — the whole editor: screen, format toolbar, and the two text-transform helpers
  (`wrapSelectionWithSyntax`, `insertLinePrefix`). / 编辑器全部实现：界面、格式工具栏与两个文本处理函数
