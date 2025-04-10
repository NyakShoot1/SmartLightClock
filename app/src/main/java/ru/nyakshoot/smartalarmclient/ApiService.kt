package ru.nyakshoot.smartalarmclient

import android.os.Parcel
import android.os.Parcelable
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @GET("api/mqtt/latest")
    suspend fun getLatestSensorData(): SensorsData

    @GET("api/sleep/daily")
    suspend fun getDailyData(
        @Query("year") year: Int,
        @Query("month") month: Int,
        @Query("day") day: Int
    ): SleepData

    @POST("api/sleep/sleep_quality")
    suspend fun sendSleepQuality(
        @Query("rating") rating: Int
    ): SleepQualityResponse

    @POST("api/mqtt/set_alarm")
    suspend fun setAlarmTime(
        @Query("time") seconds: Int
    ): AlarmResponse

    @POST("api/mqtt/cancel_alarm")
    suspend fun cancelAlarm(): AlarmResponse

    // Этот эндпоинт нужно добавить на сервер для проверки статуса будильника
    @GET("api/mqtt/sleep_rating_status")
    suspend fun getSleepRatingStatus(): SleepRatingStatusResponse

    @GET("api/mqtt/alarm_status")
    suspend fun getAlarmStatus(): AlarmStatusResponse
}

// Добавляем новую модель для ответа на установку будильника
data class AlarmResponse(
    val message: String,
    val time: Int
)