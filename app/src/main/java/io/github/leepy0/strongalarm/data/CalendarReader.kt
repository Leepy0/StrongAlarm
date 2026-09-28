package io.github.leepy0.strongalarm.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import android.provider.CalendarContract
import android.util.Log
import io.github.leepy0.strongalarm.core.CalendarEvent
import java.time.LocalDate
import java.time.ZoneId

object CalendarReader {

    data class CalendarInfo(
        val id: Long,
        val name: String,
        val account: String,
        val holidayLike: Boolean,
    )

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** 권한 있고 잠금 해제된 상태여야 캘린더 Provider 사용 가능 */
    fun isAvailable(ctx: Context): Boolean =
        hasPermission(ctx) && ctx.getSystemService(UserManager::class.java).isUserUnlocked

    /** from~to 날짜 범위의 일정. 읽을 수 없으면 null */
    fun readEvents(ctx: Context, from: LocalDate, to: LocalDate, zone: ZoneId): List<CalendarEvent>? {
        if (!isAvailable(ctx)) return null
        return try {
            // 종일 일정은 UTC 자정 기준으로 저장되므로 앞뒤 하루씩 넓혀 조회
            val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, begin)
                ContentUris.appendId(it, end)
            }.build()
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.STATUS,
            )
            val out = mutableListOf<CalendarEvent>()
            ctx.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(0) ?: continue
                    // 취소된 일정 제외
                    if (!c.isNull(5) && c.getInt(5) == CalendarContract.Events.STATUS_CANCELED) continue
                    out += CalendarEvent(
                        title = title,
                        calendarId = c.getLong(1),
                        allDay = c.getInt(2) == 1,
                        begin = c.getLong(3),
                        end = c.getLong(4),
                    )
                }
            }
            out
        } catch (e: Exception) {
            Log.e("CalendarReader", "일정 조회 실패", e)
            null
        }
    }

    fun listCalendars(ctx: Context): List<CalendarInfo> {
        if (!isAvailable(ctx)) return emptyList()
        return try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.OWNER_ACCOUNT,
            )
            val out = mutableListOf<CalendarInfo>()
            ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: "(이름 없음)"
                    val owner = c.getString(3) ?: ""
                    out += CalendarInfo(
                        id = c.getLong(0),
                        name = name,
                        account = c.getString(2) ?: "",
                        holidayLike = owner.contains("#holiday@") ||
                            listOf("휴일", "공휴일", "holiday").any { name.contains(it, ignoreCase = true) },
                    )
                }
            }
            out.sortedWith(compareBy({ !it.holidayLike }, { it.account }, { it.name }))
        } catch (e: Exception) {
            Log.e("CalendarReader", "캘린더 목록 조회 실패", e)
            emptyList()
        }
    }
}
