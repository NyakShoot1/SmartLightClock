package ru.nyakshoot.smartalarmclient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class SleepRepository {
    private val apiService: ApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.147.19.211:8000/") // Для эмулятора
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }

    // Получение текущих данных с сенсоров
    suspend fun getLatestSensorData(): SensorsData = withContext(Dispatchers.IO) {
        val response = apiService.getLatestSensorData()
        response
    }

    // Получение данных за день
    suspend fun getDailyData(year: Int, month: Int, day: Int): SleepData = withContext(Dispatchers.IO) {
        val response = apiService.getDailyData(year, month, day)
        response
    }

    // Проверка, можно ли оценить сон сегодня
    suspend fun checkIfCanRateSleepToday(): Boolean = withContext(Dispatchers.IO) {
        try {
            // MQTT запрос для проверки статуса будильника
            val response = apiService.getSleepRatingStatus()
            response.canRate
        } catch (e: Exception) {
            false
        }
    }

    // Отправка оценки качества сна
    suspend fun sendSleepQuality(rating: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.sendSleepQuality(rating)
            response.message == "Sleep quality recorded successfully"
        } catch (e: Exception) {
            false
        }
    }

    // Установка времени будильника
    suspend fun setAlarmTime(seconds: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.setAlarmTime(seconds)
            response.message == "Alarm time set"
        } catch (e: Exception) {
            false
        }
    }

    // Отмена будильника
    suspend fun cancelAlarm(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.cancelAlarm()
            response.message == "Alarm canceled"
        } catch (e: Exception) {
            false
        }
    }

    // Получение статуса будильника
    suspend fun getAlarmStatus(): AlarmStatusResponse = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAlarmStatus()
            response
        } catch (e: Exception) {
            // В случае ошибки возвращаем дефолтный статус
            AlarmStatusResponse(active = false, triggered = false)
        }
    }
}