package com.rokidbrew.phone

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DetailInfoPanel(app: BrewApp, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = BrewPanel.copy(alpha = 0.76f)),
        border = BorderStroke(1.dp, BrewBorderHi.copy(alpha = 0.46f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DetailSectionTitle("About")
                Spacer(Modifier.weight(1f))
            }
            AboutBody(app.aboutText(), Modifier.padding(top = 11.dp))
            SourceLine(app, Modifier.padding(top = 18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(1.dp)
                    .background(BrewBorderHi.copy(alpha = 0.32f)),
            )
            WhatsNewSection(app, Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
internal fun DetailSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        color = BrewTextBright,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

@Composable
internal fun AboutBody(text: String, modifier: Modifier = Modifier) {
    DetailMarkdownBody(text, modifier)
}

@Composable
internal fun DetailMarkdownBody(
    text: String,
    modifier: Modifier = Modifier,
    maxBlocks: Int = Int.MAX_VALUE,
) {
    val blocks = remember(text, maxBlocks) { markdownDescriptionBlocks(text).take(maxBlocks) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block.kind) {
                DetailBlockKind.Heading -> Text(
                    block.text,
                    color = BrewTextBright,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 15.dp),
                )
                DetailBlockKind.Bullet -> DetailBulletLine(
                    block.text,
                    Modifier.padding(top = if (index == 0) 0.dp else 7.dp),
                )
                DetailBlockKind.Paragraph -> Text(
                    block.text,
                    color = BrewText,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 11.dp),
                )
            }
        }
    }
}

private enum class DetailBlockKind {
    Heading,
    Paragraph,
    Bullet,
}

private data class DetailTextBlock(
    val kind: DetailBlockKind,
    val text: String,
)

private fun markdownDescriptionBlocks(text: String): List<DetailTextBlock> {
    val cleaned = text
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    if (cleaned.isBlank()) return emptyList()

    val blocks = mutableListOf<DetailTextBlock>()
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        val joined = paragraph.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        if (joined.isNotBlank()) {
            blocks += DetailTextBlock(DetailBlockKind.Paragraph, cleanInlineMarkdown(joined))
        }
        paragraph.clear()
    }

    cleaned.lines().forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isBlank()) {
            flushParagraph()
            return@forEach
        }

        val heading = Regex("^#{1,6}\\s+(.+)$").matchEntire(line)
        if (heading != null) {
            flushParagraph()
            blocks += DetailTextBlock(DetailBlockKind.Heading, cleanInlineMarkdown(heading.groupValues[1]))
            return@forEach
        }

        val structuralLine = cleanInlineMarkdown(line)
        val bracketHeading = Regex("^\\[([^]]+)]$").matchEntire(structuralLine)
        if (bracketHeading != null) {
            flushParagraph()
            blocks += DetailTextBlock(DetailBlockKind.Heading, bracketHeading.groupValues[1].trim())
            return@forEach
        }

        val emphasizedHeading = isEmphasizedLine(line)
        val numberedHeading = Regex("^\\d+[.)]\\s+(.+)$").matchEntire(structuralLine)
        if (emphasizedHeading && numberedHeading != null) {
            flushParagraph()
            blocks += DetailTextBlock(DetailBlockKind.Heading, numberedHeading.groupValues[1].trim())
            return@forEach
        }

        val bullet = Regex("^[-*+]\\s+(.+)$").matchEntire(line)
            ?: Regex("^\\d+[.)]\\s+(.+)$").matchEntire(line)
        if (bullet != null) {
            flushParagraph()
            blocks += DetailTextBlock(DetailBlockKind.Bullet, cleanInlineMarkdown(bullet.groupValues[1]))
            return@forEach
        }

        if (Regex("^-{3,}$").matches(line)) {
            flushParagraph()
            return@forEach
        }

        paragraph += line
    }
    flushParagraph()

    return blocks.ifEmpty {
        listOf(DetailTextBlock(DetailBlockKind.Paragraph, cleanInlineMarkdown(cleaned.replace('\n', ' '))))
    }
}

private fun isEmphasizedLine(value: String): Boolean {
    val line = value.trim()
    return (line.startsWith("**") && line.endsWith("**") && line.length > 4) ||
        (line.startsWith("__") && line.endsWith("__") && line.length > 4)
}

private fun cleanInlineMarkdown(value: String): String {
    return value
        .replace(Regex("!\\[[^]]*]\\([^)]+\\)"), "")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        .replace(Regex("__([^_]+)__"), "$1")
        .replace(Regex("\\*([^*]+)\\*"), "$1")
        .replace(Regex("_([^_]+)_"), "$1")
        .replace(Regex("\\s+"), " ")
        .trim()
}

@Composable
internal fun DetailBulletLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(BrewGreen),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = BrewText,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun WhatsNewSection(app: BrewApp, modifier: Modifier = Modifier) {
    val release = app.releases.firstOrNull()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DetailSectionTitle("What's New")
            Spacer(Modifier.weight(1f))
            if (release?.sourceReleaseUrl != null) {
                Text("View full changelog", color = BrewGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (release == null) {
            Text(
                "Release notes will appear here when the registry imports GitHub Releases.",
                color = BrewMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            return
        }
        val title = buildList {
            release.version?.let { add("v$it") }
            release.date?.take(10)?.let { add(it) }
        }.joinToString(" / ").ifBlank { "Latest release" }
        Text(
            title,
            color = BrewGreen,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        release.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            DetailMarkdownBody(notes, Modifier.padding(top = 8.dp), maxBlocks = 5)
        }
        release.changes.take(4).forEach { change ->
            DetailBulletLine(change, Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
internal fun SourceLine(app: BrewApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BrewPanelAlt.copy(alpha = 0.88f))
            .border(1.dp, BrewBorderHi.copy(alpha = 0.52f), RoundedCornerShape(12.dp))
            .clickable(enabled = app.sourceUrl != null) {
                app.sourceUrl?.let { sourceUrl ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)))
                    }
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(BrewTextBright),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_github_mark),
                contentDescription = "GitHub",
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text("GitHub", color = BrewTextBright, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(10.dp))
        Text(
            app.author,
            color = BrewMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.34f),
        )
        app.sourceUrl?.let { sourceUrl ->
            Spacer(Modifier.width(8.dp))
            Text(
                sourceUrl.removePrefix("https://"),
                color = BrewGreen,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.66f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.KeyboardArrowRight, null, tint = BrewMuted, modifier = Modifier.size(21.dp))
        }
    }
}
