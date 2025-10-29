package com.asimut.wear.tile

import android.content.Intent
import androidx.wear.tiles.ActionBuilders
import androidx.wear.tiles.DeviceParameters
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.material.layouts.PrimaryLayout
import androidx.wear.tiles.material.Text
import androidx.wear.tiles.material.ChipDefaults
import androidx.wear.tiles.material.PrimaryChip
import com.asimut.wear.R
import com.asimut.wear.data.CardRepository
import com.asimut.wear.ui.CardListActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"
private const val RESOURCE_ID_TILE_ICON = "tile_icon"

class CardTileService : TileService() {

    private val repository: CardRepository by lazy { CardRepository.getInstance(applicationContext) }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val deviceParameters = requestParams.deviceParameters ?: DeviceParameters.Builder().build()
        val card = runBlocking(Dispatchers.IO) { repository.getPrimaryCard() }
        val tile = buildTile(deviceParameters, card)
        return Futures.immediateFuture(tile)
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .addIdToImageMapping(
                RESOURCE_ID_TILE_ICON,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(R.drawable.ic_tile_primary)
                            .build()
                    )
                    .build()
            )
            .build()
        return Futures.immediateFuture(resources)
    }

    private fun buildTile(
        deviceParameters: DeviceParameters,
        card: CardRepository.CardEntry?
    ): TileBuilders.Tile {
        val title = card?.payload?.title ?: getString(R.string.tile_label)
        val subtitle = card?.payload?.subtitle ?: card?.payload?.description ?: getString(R.string.tile_subtitle)
        val contentText = card?.payload?.fields?.entries?.firstOrNull()?.let { (key, value) -> "$key: $value" }
            ?: getString(R.string.tile_subtitle)

        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(CardListActivity::class.java.name)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .build()
            )
            .build()

        val clickable = ActionBuilders.Clickable.Builder()
            .setId("open_cards")
            .setOnClick(launchAction)
            .build()

        val chip = PrimaryChip.Builder(this, getString(R.string.tile_label))
            .setIconContent(
                androidx.wear.tiles.material.Icon.Builder()
                    .setResourceId(RESOURCE_ID_TILE_ICON)
                    .build()
            )
            .setPrimaryChipColors(ChipDefaults.primaryChipColors())
            .setSecondaryLabel(getString(R.string.tile_subtitle))
            .setContentDescription(getString(R.string.tile_label))
            .setClickable(clickable)
            .build()

        val layout = PrimaryLayout.Builder(deviceParameters)
            .setPrimaryLabelText(title)
            .setSecondaryLabelText(subtitle)
            .setContent(
                Text.Builder(this, contentText)
                    .setMaxLines(2)
                    .build()
            )
            .setPrimaryChipContent(chip)
            .build()

        val layoutElement = TileBuilders.Layout.Builder().setRoot(layout).build()
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(layoutElement)
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(60_000)
            .setTimeline(timeline)
            .build()
    }
}
