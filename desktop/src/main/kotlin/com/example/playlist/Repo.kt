package com.example.playlist

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.IOException

data class Track(
    val url: String,
    val title: String,
    val artist: String,
    val thumb: String?,
    val durationSec: Long
)

data class Playlist(
    val id: String,
    val name: String,
    val url: String, // 비어 있으면 앱 안에서 만든 플레이리스트
    val tracks: List<Track>,
    val tag: String = ""
)

const val SEARCH_PID = "__search__"

/** 텍스트에서 링크 찾기 (플레이리스트 링크 우선) */
fun extractUrl(text: String): String? {
    val all = Regex("https?://\\S+").findAll(text).map { it.value.trimEnd('.', ',', ')') }.toList()
    return all.firstOrNull { it.contains("list=") } ?: all.firstOrNull()
}

fun listIdOf(url: String): String? =
    Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1)

object YtRepo {
    private const val MAX_TRACKS = 500

    fun loadPlaylist(url: String): Pair<String, List<Track>> {
        val service = ServiceList.YouTube
        val info = PlaylistInfo.getInfo(service, url)
        val items = info.relatedItems.toMutableList()
        var page = info.nextPage
        while (page != null && items.size < MAX_TRACKS) {
            val more = PlaylistInfo.getMoreItems(service, url, page)
            items += more.items
            page = more.nextPage
        }
        val tracks = items
            .filter { it.url.isNotBlank() }
            .map {
                Track(
                    url = it.url,
                    title = it.name,
                    artist = it.uploaderName ?: "",
                    thumb = it.thumbnails.lastOrNull()?.url,
                    durationSec = it.duration
                )
            }
        return info.name to tracks
    }

    fun search(query: String): List<Track> {
        val svc = ServiceList.YouTube
        val handler = svc.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val info = SearchInfo.getInfo(svc, handler)
        return info.relatedItems.filterIsInstance<StreamInfoItem>()
            .filter { it.url.isNotBlank() }
            .map {
                Track(
                    url = it.url,
                    title = it.name,
                    artist = it.uploaderName ?: "",
                    thumb = it.thumbnails.lastOrNull()?.url,
                    durationSec = it.duration
                )
            }
    }

    /** 영상 URL -> m4a 오디오 주소 (PC 재생기는 m4a만 재생 가능) */
    fun resolveAudioUrl(videoUrl: String, low: Boolean = false): String {
        val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        val m4a = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl && it.format == MediaFormat.M4A
        }
        val best = (if (low) m4a.minByOrNull { it.averageBitrate } else m4a.maxByOrNull { it.averageBitrate })
            ?: throw IOException("재생할 수 있는 m4a 오디오가 없어요")
        return best.content
    }
}
