package ru.nyakshoot.smartalarmclient

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import ru.nyakshoot.smartalarmclient.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
    private var lastRefreshTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        viewModel.initialize(this)

        // Проверяем статус будильника при запуске
        viewModel.refreshSensorData()

        // Установка обработчиков для навигации по датам
        setupDateNavigation()

        // Установка обработчика для кнопки обновления
        binding.fabRefresh.setOnClickListener {
            refreshData()
        }

        // Настройка будильника
        setupAlarm()

        // Настройка оценки сна
        setupSleepRating()

        // Загрузка начальных данных
        refreshData()
        updateLastUpdatedText()
    }

    private fun setupDateNavigation() {
        // Обработчик для выбора даты
        binding.dateSelectionLayout.setOnClickListener {
            showDatePickerDialog()
        }

        // Наблюдение за выбранной датой
        viewModel.getSelectedDate().let {
            updateSelectedDateText(it)
        }
    }

    private fun showDatePickerDialog() {
        val selectedDate = viewModel.getSelectedDate()
        val year = selectedDate.get(Calendar.YEAR)
        val month = selectedDate.get(Calendar.MONTH)
        val day = selectedDate.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                viewModel.setSelectedDate(selectedYear, selectedMonth + 1, selectedDay)
                val newDate = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay)
                }
                updateSelectedDateText(newDate)
            },
            year, month, day
        ).show()
    }

    private fun updateSelectedDateText(date: Calendar) {
        binding.selectedDateText.text = dateFormat.format(date.time)
    }

    private fun setupAlarm() {
        // Наблюдение за временем будильника
        viewModel.alarmTime.observe(this) { seconds ->
            if (seconds > 0) {
                // Получаем сохраненные часы и минуты для визуального отображения
                val prefs = getSharedPreferences("smart_alarm_prefs", Context.MODE_PRIVATE)
                val hour = prefs.getInt("alarm_hour", 0)
                val minute = prefs.getInt("alarm_minute", 0)
                val timestamp = prefs.getLong("alarm_timestamp", 0L)
                
                // Получаем дату из сохраненного timestamp
                val alarmDate = Calendar.getInstance()
                if (timestamp > 0) {
                    alarmDate.timeInMillis = timestamp
                    val today = Calendar.getInstance()
                    
                    // Определяем, установлен ли будильник на сегодня или завтра
                    val dateFormat = SimpleDateFormat("dd.MM", Locale("ru"))
                    val dayDiff = alarmDate.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR)
                    
                    val timeText = String.format("%02d:%02d", hour, minute)
                    
                    if (dayDiff == 0) {
                        binding.currentAlarmTimeText.text = "Сегодня в $timeText"
                    } else if (dayDiff == 1 || (dayDiff < 0 && dayDiff > -364)) { 
                        binding.currentAlarmTimeText.text = "Завтра в $timeText"
                    } else {
                        val dateText = dateFormat.format(alarmDate.time)
                        binding.currentAlarmTimeText.text = "$dateText в $timeText"
                    }
                } else {
                    // Если нет сохраненного timestamp, используем старый формат
                    binding.currentAlarmTimeText.text = String.format("%02d:%02d", hour, minute)
                }
                
                binding.cancelAlarmButton.visibility = View.VISIBLE
            } else {
                binding.currentAlarmTimeText.text = "Не установлен"
                binding.cancelAlarmButton.visibility = View.GONE
            }
        }

        // Наблюдение за статусом установки будильника
        viewModel.alarmSetStatus.observe(this) { success ->
            if (success) {
                binding.alarmStatusText.apply {
                    text = "Будильник успешно установлен"
                    setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                    visibility = View.VISIBLE
                }
            } else {
                binding.alarmStatusText.apply {
                    text = "Ошибка при установке будильника"
                    setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                    visibility = View.VISIBLE
                }
            }

            // Скрытие статуса через 3 секунды
            Handler(Looper.getMainLooper()).postDelayed({
                binding.alarmStatusText.visibility = View.GONE
            }, 3000)
        }

        // Обработчик для кнопки установки будильника
        binding.setAlarmButton.setOnClickListener {
            showTimePickerDialog()
        }

        // Обработчик для кнопки отмены будильника
        binding.cancelAlarmButton.setOnClickListener {
            viewModel.cancelAlarm()
            binding.alarmStatusText.apply {
                text = "Будильник успешно отменен"
                setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                visibility = View.VISIBLE
            }
            
            // Скрытие статуса через 3 секунды
            Handler(Looper.getMainLooper()).postDelayed({
                binding.alarmStatusText.visibility = View.GONE
            }, 3000)
        }
    }

    private fun showTimePickerDialog() {
        val alarmTimeSeconds = viewModel.alarmTime.value ?: -1
        val initialHour: Int
        val initialMinute: Int

        if (alarmTimeSeconds > 0) {
            initialHour = alarmTimeSeconds / 3600
            initialMinute = (alarmTimeSeconds % 3600) / 60
        } else {
            val currentTime = Calendar.getInstance()
            initialHour = currentTime.get(Calendar.HOUR_OF_DAY)
            initialMinute = currentTime.get(Calendar.MINUTE)
        }

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                viewModel.setAlarmTime(hourOfDay, minute)
            },
            initialHour, initialMinute, true
        ).show()
    }

    private fun setupSleepRating() {
        // Настройка слайдера оценки сна
        binding.sleepRatingSlider.addOnChangeListener { _, value, _ ->
            binding.sleepRatingValue.text = value.toInt().toString()
        }

        // Обработчик для кнопки отправки оценки
        binding.submitRatingButton.setOnClickListener {
            val rating = binding.sleepRatingSlider.value.toInt()
            viewModel.submitSleepRating(rating)
        }

        // Наблюдение за возможностью оценить сон
        viewModel.canRateSleepToday.observe(this) { canRate ->
            if (canRate) {
                binding.ratingLayout.visibility = View.VISIBLE
                binding.ratingCompleteText.visibility = View.GONE
            } else {
                binding.ratingLayout.visibility = View.GONE
                if (viewModel.hasRatedToday.value == true) {
                    binding.ratingCompleteText.visibility = View.VISIBLE
                } else {
                    binding.ratingCompleteText.visibility = View.GONE
                }
            }
        }

        // Наблюдение за статусом отправки оценки
        viewModel.ratingSubmissionStatus.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Оценка успешно отправлена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Ошибка при отправке оценки", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshData() {
        // Обновление данных с сенсоров
        viewModel.refreshSensorData()

        // Обновление данных за день
        viewModel.refreshDailyData()

        // Проверка возможности оценить сон
        viewModel.checkSleepRatingAvailability()

        // Обновление времени последнего обновления
        lastRefreshTime = System.currentTimeMillis()
        updateLastUpdatedText()
    }

    private fun updateLastUpdatedText() {
        if (lastRefreshTime > 0) {
            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val formattedTime = dateFormat.format(Date(lastRefreshTime))
            binding.lastUpdatedText.text = getString(R.string.last_updated, formattedTime)
        }
    }

    // Обработчики для отображения данных от ViewModel
    @SuppressLint("DefaultLocale")
    override fun onStart() {
        super.onStart()

        // Наблюдение за текущими данными сенсоров
        viewModel.currentSensorData.observe(this) { data ->
            if (data != null) {
                binding.tempValue.text = String.format("%.1f °C", data.temperature)
                binding.humidityValue.text = String.format("%.1f %%", data.humidity)
            }
        }

        // Наблюдение за данными за день
        viewModel.dailyData.observe(this) { data ->
            if (data != null) {
                updateDataDisplay(data)
                binding.chartNoDataText.visibility = View.GONE
                binding.sleepDataContainer.visibility = View.VISIBLE
            } else {
                // Проверяем, не выбрана ли будущая дата
                val today = Calendar.getInstance()
                val selectedDate = viewModel.getSelectedDate()
                
                if (selectedDate.after(today)) {
                    binding.chartNoDataText.text = "Нельзя просмотреть данные для будущих дней"
                } else {
                    binding.chartNoDataText.text = "Нет данных для выбранного дня"
                }
                
                binding.chartNoDataText.visibility = View.VISIBLE
                binding.sleepDataContainer.visibility = View.GONE
            }
        }
    }

    private fun updateDataDisplay(data: SleepData) {
        // Обновляем значения температуры
        val tempProgress = data.temperature.toInt().coerceIn(0, 40)
        binding.temperatureProgress.progress = tempProgress
        binding.temperatureValue.text = String.format("%.1f °C", data.temperature)

        // Обновляем значения влажности
        val humidityProgress = data.humidity.toInt().coerceIn(0, 100)
        binding.humidityProgress.progress = humidityProgress
        binding.humidityDataValue.text = String.format("%.1f %%", data.humidity)

        // Обновляем значения оценки сна
        val ratingValue = data.sleepRating.coerceIn(0, 10)
        binding.ratingProgress.progress = ratingValue
        binding.ratingDataValue.text = if (ratingValue > 0) {
            String.format("%d/10", ratingValue)
        } else {
            "Нет оценки"
        }
        
        // Обновляем значение времени пробуждения
        if (data.wakeTime != null && data.wakeTime > 0) {
            // Преобразуем миллисекунды в минуты и секунды
            val minutes = (data.wakeTime / 1000 / 60).toInt()
            val seconds = ((data.wakeTime / 1000) % 60).toInt()
            
            if (minutes > 0) {
                binding.wakeTimeValue.text = String.format("%d мин. %02d сек.", minutes, seconds)
            } else {
                binding.wakeTimeValue.text = String.format("%d сек.", seconds)
            }
            
            binding.wakeTimeContainer.visibility = View.VISIBLE
        } else {
            binding.wakeTimeValue.text = "Нет данных"
            binding.wakeTimeContainer.visibility = View.VISIBLE
        }
    }
}