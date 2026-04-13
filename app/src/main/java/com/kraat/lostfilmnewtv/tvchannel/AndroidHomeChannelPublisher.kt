package com.kraat.lostfilmnewtv.tvchannel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.core.content.ContextCompat
import com.kraat.lostfilmnewtv.MainActivity
import com.kraat.lostfilmnewtv.R
import com.kraat.lostfilmnewtv.navigation.AppLaunchTarget

internal fun isUsableChannelImage(url: String): Boolean {
    if (url.isBlank()) return false
    return url.startsWith("https://") || url.startsWith("http://")
}

class AndroidHomeChannelPublisher(
    private val appContext: Context,
    private val helperFacade: PreviewChannelHelperFacade = AndroidXPreviewChannelHelperFacade(appContext),
    private val channelLogoProvider: (() -> Bitmap)? = null,
) : HomeChannelPublisher {
    override suspend fun reconcile(
        mode: AndroidTvChannelMode,
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult {
        return reconcileChannel(
            channel = buildPrimaryChannelRecord(mode),
            existingChannelId = existingChannelId,
            programs = programs,
        )
    }

    override suspend fun reconcileFavorites(
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult {
        return reconcileChannel(
            channel = buildFavoritesChannelRecord(),
            existingChannelId = existingChannelId,
            programs = programs,
        )
    }

    private suspend fun reconcileChannel(
        channel: PreviewChannelRecord,
        existingChannelId: Long?,
        programs: List<HomeChannelProgram>,
    ): HomeChannelPublisherResult {
        val channelId = resolveChannelId(existingChannelId, channel)
        helperFacade.requestChannelBrowsable(channelId)
        val eligiblePrograms = programs.filter(::isChannelEligible)
        val existingProgramIds = helperFacade.getPrograms(channelId)
            .mapNotNull { it.programId }

        // Re-publish the whole row to force launcher refresh on devices that keep
        // stale card metadata or horizontal position across update-in-place calls.
        for (programId in existingProgramIds) {
            helperFacade.deleteProgram(programId)
        }

        eligiblePrograms.forEachIndexed { index, program ->
            val posterImage = channelPosterImageFor(program)
            val thumbnailImage = channelThumbnailImageFor(program, posterImage)
            helperFacade.upsertProgram(
                PreviewProgramRecord(
                    internalProviderId = program.internalProviderId,
                    channelId = channelId,
                    title = program.title,
                    description = program.description,
                    posterUrl = posterImage.asUriOrNull()?.toString().orEmpty(),
                    thumbnailUrl = thumbnailImage.asUriOrNull()?.toString().orEmpty(),
                    launchIntent = AppLaunchTarget.createDetailsIntent(appContext, program.detailsUrl),
                    weight = eligiblePrograms.size - index,
                ),
            )
        }

        return HomeChannelPublisherResult(channelId)
    }

    override suspend fun deleteChannel(channelId: Long) {
        helperFacade.deleteChannel(channelId)
    }

    private suspend fun resolveChannelId(
        existingChannelId: Long?,
        channel: PreviewChannelRecord,
    ): Long {
        val validExistingChannelId = existingChannelId
            ?.takeIf { helperFacade.channelExists(it) }
        val matchingChannelIds = helperFacade.findChannelIdsByInternalProviderId(channel.internalProviderId)
        val channelIdToReuse = validExistingChannelId ?: matchingChannelIds.firstOrNull()

        matchingChannelIds
            .filterNot { it == channelIdToReuse }
            .forEach { duplicateChannelId ->
                helperFacade.deleteChannel(duplicateChannelId)
            }

        if (channelIdToReuse != null) {
            helperFacade.updateChannel(channelIdToReuse, channel)
            return channelIdToReuse
        }

        return helperFacade.publishDefaultChannel(channel)
    }

    private fun buildPrimaryChannelRecord(mode: AndroidTvChannelMode): PreviewChannelRecord {
        return PreviewChannelRecord(
            displayName = mode.channelDisplayName(),
            description = mode.channelDescription(),
            appLinkIntent = Intent(appContext, MainActivity::class.java),
            internalProviderId = PRIMARY_CHANNEL_INTERNAL_PROVIDER_ID,
            logoBitmap = channelLogoProvider?.invoke() ?: loadChannelLogoBitmap(),
        )
    }

    private fun buildFavoritesChannelRecord(): PreviewChannelRecord {
        return PreviewChannelRecord(
            displayName = "Избранное",
            description = "Избранные релизы LostFilm",
            appLinkIntent = Intent(appContext, MainActivity::class.java),
            internalProviderId = FAVORITES_CHANNEL_INTERNAL_PROVIDER_ID,
            logoBitmap = channelLogoProvider?.invoke() ?: loadChannelLogoBitmap(),
        )
    }

    private fun channelPosterImageFor(program: HomeChannelProgram): String {
        return program.posterUrl.takeIf(::isUsableChannelImage).orEmpty()
    }

    private fun channelThumbnailImageFor(program: HomeChannelProgram, posterImage: String): String {
        return program.backdropUrl
            .takeIf(::isUsableChannelImage)
            .orEmpty()
            .ifBlank { posterImage }
    }

    private fun isChannelEligible(program: HomeChannelProgram): Boolean {
        return channelPosterImageFor(program).isNotBlank() || channelThumbnailImageFor(program, "").isNotBlank()
    }

    private fun loadChannelLogoBitmap(): Bitmap {
        val drawable = checkNotNull(ContextCompat.getDrawable(appContext, R.drawable.ic_lf_logo)) {
            "Channel logo drawable is missing"
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 256
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 256
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun String.asUriOrNull(): Uri? = takeIf { it.isNotBlank() }?.let(Uri::parse)

    private fun AndroidTvChannelMode.channelDisplayName(): String {
        return when (this) {
            AndroidTvChannelMode.ALL_NEW -> "Новые релизы"
            AndroidTvChannelMode.UNWATCHED -> "Непросмотренные"
            AndroidTvChannelMode.DISABLED -> appContext.applicationInfo.loadLabel(appContext.packageManager).toString()
        }
    }

    private fun AndroidTvChannelMode.channelDescription(): String {
        return when (this) {
            AndroidTvChannelMode.ALL_NEW -> "Все новые релизы LostFilm"
            AndroidTvChannelMode.UNWATCHED -> "Непросмотренные релизы LostFilm"
            AndroidTvChannelMode.DISABLED -> CHANNEL_DESCRIPTION
        }
    }

    private companion object {
        const val PRIMARY_CHANNEL_INTERNAL_PROVIDER_ID = "lostfilm-home-channel"
        const val FAVORITES_CHANNEL_INTERNAL_PROVIDER_ID = "lostfilm-home-channel-favorites"
        const val CHANNEL_DESCRIPTION = "LostFilm releases"
    }
}
