package ru.nyakshoot.smartalarmclient

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings.Global.putFloat
import android.provider.Settings.Global.putLong
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.*

class MainViewModel : ViewModel() {
    private val repository = SleepRepository()

    // SharedPreferences for data persistence
    private lateinit var preferences: SharedPreferences

    // Текущие данные с сенсоров
    private val _currentSensorData = MutableLiveData<SensorsData>()
    val currentSensorData: LiveData<SensorsData> = _currentSensorData

    // Данные за день
    private val _dailyData = MutableLiveData<SleepData?>(null)
    val dailyData: LiveData<SleepData?> = _dailyData

    // Возможность оценить сон сегодня
    private val _canRateSleepToday = MutableLiveData<Boolean>(false)
    val canRateSleepToday: LiveData<Boolean> = _canRateSleepToday

    // Флаг, указывающий, был ли оценен сон сегодня
    private val _hasRatedToday = MutableLiveData<Boolean>(false)
    val hasRatedToday: LiveData<Boolean> = _hasRatedToday

    // Статус отправки оценки
    private val _ratingSubmissionStatus = MutableLiveData<Boolean>()
    val ratingSubmissionStatus: LiveData<Boolean> = _ratingSubmissionStatus

    // Время будильника в секундах
    private val _alarmTime = MutableLiveData<Int>(-1)
    val alarmTime: LiveData<Int> = _alarmTime

    // Статус отправки времени будильника
    private val _alarmSetStatus = MutableLiveData<Boolean>()
    val alarmSetStatus: LiveData<Boolean> = _alarmSetStatus

    // Выбранная дата
    private var selectedDate = Calendar.getInstance()

    // Инициализация SharedPreferences
    fun initialize(context: Context) {
        preferences = context.getSharedPreferences("smart_alarm_prefs", Context.MODE_PRIVATE)
        loadSavedData()
    }

    private fun loadSavedData() {
        // Загрузка сохраненных данных о сенсорах
        val temp = preferences.getFloat("temperature", 0f)
        val humidity = preferences.getFloat("humidity", 0f)
        if (temp > 0 && humidity > 0) {
            _currentSensorData.value = SensorsData(temp, humidity)
        }

        // Загрузка времени будильника
        val savedAlarmTime = preferences.getInt("alarm_time", -1)
        if (savedAlarmTime > -1) {
            _alarmTime.value = savedAlarmTime
        }

        // Загрузка оценки сна
        _hasRatedToday.value = preferences.getBoolean("has_rated_today", false)

        // Загрузка времени последнего обновления
        val lastUpdateLong = preferences.getLong("last_update_time", 0L)
        if (lastUpdateLong > 0) {
            // Время последнего обновления можно использовать в UI
        }
    }

    // Обновление данных с сенсоров
    fun refreshSensorData() {
        viewModelScope.launch {
            try {
                val data = repository.getLatestSensorData()
                _currentSensorData.postValue(data)

                // Сохранение данных
                preferences.edit().apply {
                    putFloat("temperature", data.temperature)
                    putFloat("humidity", data.humidity)
                    putLong("last_update_time", System.currentTimeMillis())
                    apply()
                }
                
                // Проверяем статус будильника
                checkAlarmStatus()
            } catch (e: Exception) {
                // Обработка ошибки
            }
        }
    }
    
    // Проверка статуса будильника
    private fun checkAlarmStatus() {
        viewModelScope.launch {
            try {
                val alarmStatus = repository.getAlarmStatus()
                
                // Если будильник сработал, сбрасываем настройки будильника
                if (alarmStatus.triggered) {
                    _alarmTime.postValue(-1)
                    
                    // Очищаем сохраненные данные будильника
                    preferences.edit()
                        .remove("alarm_time")
                        .remove("alarm_hour")
                        .remove("alarm_minute")
                        .remove("alarm_timestamp")
                        .apply()
                }
            } catch (e: Exception) {
                // Обработка ошибки
            }
        }
    }

    // Обновление данных за день
    fun refreshDailyData() {
        val year = selectedDate.get(Calendar.YEAR)
        val month = selectedDate.get(Calendar.MONTH) + 1
        val day = selectedDate.get(Calendar.DAY_OF_MONTH)

        // Проверяем, не выбрана ли будущая дата
        val today = Calendar.getInstance()
        val selectedDateCal = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        // Если выбрана будущая дата, сразу отправляем null
        if (selectedDateCal.after(today)) {
            _dailyData.postValue(null)
            return
        }

        viewModelScope.launch {
            try {
                val data = repository.getDailyData(year, month, day)
                _dailyData.postValue(data)

                // Если текущий день, проверяем состояние оценки
                val isToday = (today.get(Calendar.YEAR) == year &&
                        today.get(Calendar.MONTH) + 1 == month &&
                        today.get(Calendar.DAY_OF_MONTH) == day)

                if (isToday) {
                    _hasRatedToday.postValue(data?.sleepRating != null && data.sleepRating > 0)
                    preferences.edit().putBoolean("has_rated_today", data?.sleepRating != null && data.sleepRating > 0).apply()
                }
            } catch (e: Exception) {
                // Если данных нет, показываем пустые данные вместо тестовых
                _dailyData.postValue(null)
            }
        }
    }

    // Тестовые данные для графика (больше не используются)
    private fun getTestData(year: Int, month: Int, day: Int): SleepData {
        val random = Random()
        val date = String.format("%04d-%02d-%02d", year, month, day)
        val temperature = 20f + random.nextFloat() * 5
        val humidity = 40f + random.nextFloat() * 20
        val sleepRating = random.nextInt(5) + 5  // 5-10

        return SleepData(temperature, humidity, sleepRating)
    }

    // Проверка возможности оценить сон сегодня
    fun checkSleepRatingAvailability() {
        viewModelScope.launch {
            try {
                val canRate = repository.checkIfCanRateSleepToday()
                val today = Calendar.getInstance()
                val isToday = (today.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                        today.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH) &&
                        today.get(Calendar.DAY_OF_MONTH) == selectedDate.get(Calendar.DAY_OF_MONTH))

                _canRateSleepToday.postValue(canRate && isToday && _hasRatedToday.value != true)
            } catch (e: Exception) {
                // Обработка ошибки
            }
        }
    }

    // Отправка оценки сна
    fun submitSleepRating(rating: Int) {
        viewModelScope.launch {
            try {
                val success = repository.sendSleepQuality(rating)
                _ratingSubmissionStatus.postValue(success)

                if (success) {
                    _canRateSleepToday.postValue(false)
                    _hasRatedToday.postValue(true)
                    preferences.edit().putBoolean("has_rated_today", true).apply()
                    refreshDailyData()  // Обновляем данные, чтобы отразить новую оценку
                }
            } catch (e: Exception) {
                _ratingSubmissionStatus.postValue(false)
            }
        }
    }

    // Установка времени будильника
    fun setAlarmTime(hours: Int, minutes: Int) {
        // Проверяем, не прошло ли уже выбранное время сегодня
        val currentTime = Calendar.getInstance()
        val selectedTime = Calendar.getInstance()
        selectedTime.set(Calendar.HOUR_OF_DAY, hours)
        selectedTime.set(Calendar.MINUTE, minutes)
        selectedTime.set(Calendar.SECOND, 0)
        
        // Если выбранное время уже прошло сегодня, устанавливаем на завтра
        if (selectedTime.before(currentTime)) {
            selectedTime.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        // Рассчитываем разницу в секундах от текущего времени до установленного
        val nowMillis = currentTime.timeInMillis
        val alarmMillis = selectedTime.timeInMillis
        val diffSeconds = ((alarmMillis - nowMillis) / 1000).toInt()
        
        viewModelScope.launch {
            try {
                val success = repository.setAlarmTime(diffSeconds)
                if (success) {
                    _alarmTime.postValue(diffSeconds)
                    _alarmSetStatus.postValue(true)

                    // Сохранение времени будильника (абсолютное значение в секундах)
                    preferences.edit().putInt("alarm_time", diffSeconds).apply()
                    
                    // Сохраняем дополнительно часы и минуты для визуального отображения
                    preferences.edit()
                        .putInt("alarm_hour", hours)
                        .putInt("alarm_minute", minutes)
                        .putLong("alarm_timestamp", alarmMillis)
                        .apply()
                } else {
                    _alarmSetStatus.postValue(false)
                }
            } catch (e: Exception) {
                _alarmSetStatus.postValue(false)
            }
        }
    }

    // Отмена будильника
    fun cancelAlarm() {
        viewModelScope.launch {
            try {
                val success = repository.cancelAlarm()
                if (success) {
                    _alarmTime.postValue(-1) // Сбрасываем время будильника
                    _alarmSetStatus.postValue(true)
                    
                    // Удаляем сохраненное время будильника
                    preferences.edit().remove("alarm_time").apply()
                } else {
                    _alarmSetStatus.postValue(false)
                }
            } catch (e: Exception) {
                _alarmSetStatus.postValue(false)
            }
        }
    }

    // Установка выбранной даты
    fun setSelectedDate(year: Int, month: Int, day: Int) {
        selectedDate.set(year, month - 1, day)
        refreshDailyData()
        checkSleepRatingAvailability()
    }

    // Получение текущей выбранной даты
    fun getSelectedDate(): Calendar {
        return selectedDate
    }
}