package com.asimut.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import com.asimut.core.sync.CardSyncContract
import com.asimut.wear.data.CardRepository
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.asimut.wear.R

class CardListActivity : ComponentActivity() {

    private val viewModel: CardListViewModel by viewModels {
        CardListViewModel.Factory(CardRepository.getInstance(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val cards by viewModel.cards.collectAsState()
                val requiresUnlock = remember { !viewModel.isDeviceSecure() }
                val configuration = LocalConfiguration.current
                val isRound = configuration.isScreenRound
                CardListScreen(
                    cards = cards,
                    onSetPrimary = { id -> viewModel.setPrimary(id) },
                    onRefresh = { requestRefreshFromPhone() },
                    isRound = isRound,
                    requiresUnlock = requiresUnlock
                )
            }
        }
        viewModel.ensurePrimaryCard()
    }

    private fun requestRefreshFromPhone() {
        lifecycleScope.launch(Dispatchers.IO) {
            val nodeClient = Wearable.getNodeClient(this@CardListActivity)
            val nodes = nodeClient.connectedNodes.await()
            val messageClient = Wearable.getMessageClient(this@CardListActivity)
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, CardSyncContract.PATH_REFRESH_REQUEST, ByteArray(0)).await()
            }
        }
    }
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun CardListScreen(
    cards: List<CardRepository.CardEntry>,
    onSetPrimary: (String) -> Unit,
    onRefresh: () -> Unit,
    isRound: Boolean,
    requiresUnlock: Boolean,
    modifier: Modifier = Modifier
) {
    val pagerState: PagerState = rememberPagerState(pageCount = { cards.size.coerceAtLeast(1) })
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = {
            if (cards.isNotEmpty()) {
                PositionIndicator(pagerState = pagerState)
            }
        }
    ) { padding ->
        if (cards.isEmpty()) {
            EmptyState(
                modifier = modifier.padding(padding),
                onRefresh = onRefresh,
                requiresUnlock = requiresUnlock
            )
        } else {
            CardCarousel(
                cards = cards,
                onSetPrimary = onSetPrimary,
                pagerState = pagerState,
                onRefresh = onRefresh,
                isRound = isRound,
                requiresUnlock = requiresUnlock,
                modifier = modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier, onRefresh: () -> Unit, requiresUnlock: Boolean) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.empty_state_title),
            style = MaterialTheme.typography.title3,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(id = R.string.empty_state_body),
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (requiresUnlock) {
            Text(
                text = stringResource(id = R.string.pin_required),
                style = MaterialTheme.typography.caption1,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Chip(
            onClick = onRefresh,
            label = { Text(text = stringResource(id = R.string.refresh_button)) },
            icon = {
                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
            },
            modifier = Modifier.size(width = 200.dp, height = 48.dp),
            colors = ChipDefaults.primaryChipColors()
        )
    }
}
