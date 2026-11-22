package com.example.playlist

import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 개발자용 오류를 알아듣기 쉬운 문장으로 바꿔요 */
fun friendlyError(e: Throwable): String = when {
    e is PrivateContentException -> "비공개 플레이리스트예요. 공개나 일부 공개로 바꿔야 불러올 수 있어요"
    e is ContentNotAvailableException -> "삭제되었거나 볼 수 없는 플레이리스트예요"
    e is ReCaptchaException -> "유튜브가 요청을 잠시 막았어요. 조금 뒤에 다시 시도해주세요"
    e is UnknownHostException || e is SocketTimeoutException || e.cause is UnknownHostException ->
        "인터넷에 연결되어 있지 않아요"
    e is ParsingException -> "유튜브 정보를 읽지 못했어요. 링크를 확인하거나, 계속되면 설정 → 앱 정보를 봐주세요"
    e is ExtractionException -> "유튜브 정보를 읽지 못했어요. 유튜브 변경으로 앱 업데이트가 필요할 수 있어요 (설정 → 앱 정보)"
    e is IOException -> "네트워크 문제로 불러오지 못했어요"
    else -> "문제가 생겼어요 (${e.javaClass.simpleName})"
}
