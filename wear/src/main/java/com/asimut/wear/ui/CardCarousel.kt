package com.asimut.wear.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.IconButton
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListScope
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import com.asimut.core.sync.PassPayload
import com.asimut.wear.data.CardRepository
import com.asimut.wear.ui.components.CardField
import com.asimut.wear.ui.components.DeutschlandTicketDetails
import com.asimut.wear.ui.components.MensaCardDetails
import com.asimut.wear.ui.components.QrCodeView
import com.asimut.wear.ui.components.StudentCardDetails
import com.asimut.wear.ui.components.Title
import com.asimut.wear.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun CardCarousel(
    cards: List<CardRepository.CardEntry>,
    onSetPrimary: (String) -> Unit,
    pagerState: PagerState,
    onRefresh: () -> Unit,
    isRound: Boolean,
    requiresUnlock: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val entry = cards[page]
            CardDetailPage(
                entry = entry,
                isRound = isRound,
                requiresUnlock = requiresUnlock,
                onSetPrimary = { onSetPrimary(entry.payload.id) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Chip(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            onClick = onRefresh,
            label = { Text(text = stringResource(id = R.string.refresh_button)) },
            icon = {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
            },
            colors = ChipDefaults.secondaryChipColors()
        )
    }
}

@Composable
private fun CardDetailPage(
    entry: CardRepository.CardEntry,
    isRound: Boolean,
    requiresUnlock: Boolean,
    onSetPrimary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (isRound) 8.dp else 4.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        state = listState
    ) {
        item {
            CardHeader(entry = entry, onSetPrimary = onSetPrimary)
        }
        if (requiresUnlock) {
            item("secure_notice") {
                Text(
                    text = stringResource(id = R.string.pin_required),
                    style = MaterialTheme.typography.caption1,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        entry.imageBytes?.let { bytes ->
            item("image") {
                val bitmap = remember(bytes) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
        when (entry.payload.type) {
            PassPayload.CardType.STUDENT_CARD -> StudentCardDetails(entry.payload)
            PassPayload.CardType.DEUTSCHLANDTICKET -> DeutschlandTicketDetails(entry.payload)
            PassPayload.CardType.MENSA_CARD -> MensaCardDetails(entry.payload)
            else -> GenericCardDetails(entry.payload)
        }
        entry.payload.barcode?.let { barcode ->
            item {
                Spacer(modifier = Modifier.height(8.dp))
                QrCodeView(barcode = barcode)
            }
        }
    }
}

@Composable
private fun ScalingLazyListScope.GenericCardDetails(payload: PassPayload) {
    payload.fields.forEach { (key, value) ->
        item(key) {
            CardField(title = key, value = value)
        }
    }
}

@Composable
private fun CardHeader(entry: CardRepository.CardEntry, onSetPrimary: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Title(text = entry.payload.title, subtitle = entry.payload.subtitle)
        entry.lastUpdatedText?.let { updated ->
            Text(
                text = stringResource(id = R.string.last_updated, updated),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        PrimaryToggleButton(isPrimary = entry.isPrimary, onClick = onSetPrimary)
    }
}

@Composable
private fun PrimaryToggleButton(isPrimary: Boolean, onClick: () -> Unit) {
    val icon = if (isPrimary) Icons.Rounded.Star else Icons.Rounded.StarOutline
    val contentColor = if (isPrimary) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
    }
}
