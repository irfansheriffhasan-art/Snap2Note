package io.github.irfansheriffhasan.snap2note.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.irfansheriffhasan.snap2note.ui.ChatMessage
import io.github.irfansheriffhasan.snap2note.ui.StudyChatViewModel
import io.github.irfansheriffhasan.snap2note.ui.components.MarkdownText
import io.github.irfansheriffhasan.snap2note.ui.components.pageCountLabel

private val Suggestions = listOf(
  "Quiz me" to "Quiz me with 3 multiple-choice questions on these notes.",
  "Key concepts" to "List the most important concepts and definitions in these notes.",
  "Summarize" to "Summarize these notes in a few bullet points.",
  "Explain simply" to "Explain the hardest idea in these notes in simple terms, with an example.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyChatScreen(
  viewModel: StudyChatViewModel,
  hasApiKey: Boolean,
  onBack: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  val folder by viewModel.folder.collectAsStateWithLifecycle()
  val messages by viewModel.messages.collectAsStateWithLifecycle()
  val isSending by viewModel.isSending.collectAsStateWithLifecycle()
  var input by rememberSaveable { mutableStateOf("") }
  val listState = rememberLazyListState()

  LaunchedEffect(messages.size, isSending) {
    val lastIndex = messages.size + (if (isSending) 1 else 0) - 1
    if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
  }

  fun send(text: String) {
    if (text.isBlank() || isSending) return
    viewModel.send(text)
    input = ""
  }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        title = {
          Column {
            Text("Study assistant")
            Text(
              folder?.name.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        },
        actions = {
          if (messages.isNotEmpty()) {
            IconButton(onClick = viewModel::clear, enabled = !isSending) {
              Icon(Icons.Outlined.RestartAlt, contentDescription = "New conversation")
            }
          }
        },
      )
    },
    bottomBar = {
      if (hasApiKey) {
        MessageInput(
          value = input,
          onValueChange = { input = it },
          onSend = { send(input) },
          enabled = !isSending,
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    ) {
      when {
        !hasApiKey -> MissingKeyState(onOpenSettings = onOpenSettings, modifier = Modifier.align(Alignment.Center))
        messages.isEmpty() ->
          ChatIntro(
            analyzedCount = folder?.analyzedCount ?: 0,
            onSuggestion = ::send,
            modifier = Modifier.align(Alignment.Center),
          )
        else ->
          LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(messages) { message -> MessageBubble(message) }
            if (isSending) {
              item { TypingIndicator() }
            }
          }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatIntro(analyzedCount: Int, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(horizontal = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .size(64.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.tertiaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(28.dp))
    }
    Spacer(Modifier.height(20.dp))
    Text("Ask about your notes", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(
      if (analyzedCount == 0) {
        "No pages in this folder have been analyzed yet, so answers won't use your notes. Analyze pages first for answers based on your material."
      } else {
        "Answers are based on the ${pageCountLabel(analyzedCount)} you've analyzed in this folder."
      },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      Suggestions.forEach { (label, prompt) ->
        SuggestionChip(onClick = { onSuggestion(prompt) }, label = { Text(label) })
      }
    }
  }
}

@Composable
private fun MissingKeyState(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
    Spacer(Modifier.height(16.dp))
    Text("Add a Gemini API key", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Text(
      "The study assistant uses Google Gemini. Add your free API key in Settings to start chatting.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = onOpenSettings) { Text("Open settings") }
  }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
  val colors = MaterialTheme.colorScheme
  val (container, content) = when {
    message.fromUser -> colors.primary to colors.onPrimary
    message.isError -> colors.errorContainer to colors.onErrorContainer
    else -> colors.surfaceContainerHigh to colors.onSurface
  }
  val shape = if (message.fromUser) {
    RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
  } else {
    RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
  }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(color = container, contentColor = content, shape = shape, modifier = Modifier.widthIn(max = 320.dp)) {
      Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (message.fromUser || message.isError) {
          Text(message.text, style = MaterialTheme.typography.bodyMedium, color = content)
        } else {
          SelectionContainer { MarkdownText(message.text, color = content) }
        }
      }
    }
  }
}

@Composable
private fun TypingIndicator() {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
      Spacer(Modifier.size(10.dp))
      Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun MessageInput(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Ask a question") },
        maxLines = 4,
        shape = RoundedCornerShape(24.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        colors = TextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          focusedIndicatorColor = Color.Transparent,
          unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.weight(1f),
      )
      Spacer(Modifier.size(8.dp))
      FilledIconButton(onClick = onSend, enabled = enabled && value.isNotBlank(), modifier = Modifier.size(48.dp)) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
      }
    }
  }
}
