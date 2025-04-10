package ru.nyakshoot.smartalarmclient

import com.google.gson.annotations.SerializedName

data class SensorsData(
    val temperature: Float,
    val humidity: Float
)

// Модель данных о сне
data class SleepData(
    @SerializedName("temperature") // Это соответствует avg_temperature на бэкенде
    val temperature: Float,
    @SerializedName("humidity") // Это соответствует avg_humidity на бэкенде
    val humidity: Float,
    @SerializedName("sleep_rating")
    val sleepRating: Int,
    @SerializedName("wake_time")
    val wakeTime: Long? = null // Время пробуждения в миллисекундах
)

// Ответ с ежемесячными данными
data class MonthlyDataResponse(
    val data: List<SleepData>
)

// Ответ на отправку оценки сна
data class SleepQualityResponse(
    val message: String
)

// Ответ со статусом возможности оценки сна
data class SleepRatingStatusResponse(
    val canRate: Boolean
)

data class AlarmStatusResponse(
    val active: Boolean = false,
    val triggered: Boolean = false
)