#include <WiFi.h>
#include <PubSubClient.h>
#include <Adafruit_NeoPixel.h>
#include "DHTesp.h"

// Wi-Fi settings
const char* ssid = "Wokwi-GUEST";
const char* password = "";

// MQTT settings
const char* mqttServer = "192.168.1.152";
const int mqttPort = 1883;
const char* mqttUser = "";
const char* mqttPassword = "";

// MQTT topics
const char* topicAlarmTime = "alarm/time";
const char* topicAlarmStatus = "alarm/status";
const char* topicSensorData = "sensors/dht22";
const char* topicWakeTime = "alarm/wake_time";

// Pins
const int DHT_PIN = 15;
const int LED_PIN = 4;
const int NUM_LEDS = 16;
const int BUTTON_PIN = 2;
const int BUZZER_PIN = 13;

// Variables
DHTesp dhtSensor;
Adafruit_NeoPixel ring(NUM_LEDS, LED_PIN, NEO_GRB + NEO_KHZ800);
WiFiClient espClient;
PubSubClient client(espClient);

unsigned long alarmTime = 0;          // Время звукового будильника (абсолютное)
unsigned long lightTime = 0;          // Время начала свечения (абсолютное)
unsigned long lightStartTime = 0;      // Время, когда фактически начал работать свет
bool alarmActive = false;              // Активен ли будильник
bool lightActive = false;              // Активен ли свет
bool buzzerActive = false;             // Активен ли звуковой сигнал
unsigned long wakeTime = 0;            // Время пробуждения (для статистики)
const unsigned long LIGHT_DURATION = 30 * 60 * 1000; // 30 минут для плавного увеличения света
const unsigned long MIN_LIGHT_DURATION = 5 * 60 * 1000; // Минимум 5 минут для света перед будильником

void setup() {
    Serial.begin(115200);
    dhtSensor.setup(DHT_PIN, DHTesp::DHT22);
    ring.begin();
    ring.show();

    pinMode(BUTTON_PIN, INPUT_PULLUP);
    pinMode(BUZZER_PIN, OUTPUT);

    connectToWiFi();
    connectToMQTT();
}

void loop() {
    static unsigned long lastSensorUpdate = 0;
    unsigned long currentTime = millis();
    
    if (!client.connected()) {
        reconnectToMQTT();
    }
    client.loop();

    // Если будильник активен
    if (alarmActive) {
        // Если наступило время запуска света и свет еще не активен
        if (currentTime >= lightTime && !lightActive) {
            lightActive = true;
            lightStartTime = currentTime;
            Serial.println("Light sequence started at: " + String(currentTime));
            Serial.println("Time until alarm: " + String((alarmTime - currentTime) / 1000) + " seconds");
        }
        
        // Если свет активен, увеличиваем яркость постепенно
        if (lightActive) {
            // Определяем фактическую длительность света до будильника
            unsigned long actualLightDuration;
            
            if (currentTime < alarmTime) {
                // Если время будильника еще не наступило
                actualLightDuration = alarmTime - lightStartTime;
                
                // Рассчитываем прогресс
                unsigned long elapsedTime = currentTime - lightStartTime;
                if (elapsedTime <= actualLightDuration) {
                    float progress = (float)elapsedTime / actualLightDuration;
                    updateLight(progress);
                }
            } else {
                // Если время будильника уже наступило, максимальная яркость
                updateLight(1.0);
            }
        }
        
        // Если наступило время звукового будильника
        if (currentTime >= alarmTime) {
            if (!buzzerActive) {
                buzzerActive = true;
                Serial.println("Buzzer activated at: " + String(currentTime));
            }
            
            // Обновляем звуковой сигнал (меняем тоны)
            updateBuzzer();
        }
    }
    
    // Проверка кнопки отключения будильника
    if (digitalRead(BUTTON_PIN) == LOW) {
        turnOffAlarm();
    }

    // Обновляем данные с сенсоров реже, чем основной цикл
    if (currentTime - lastSensorUpdate > 5000) { // Каждые 5 секунд
        sendSensorData();
        lastSensorUpdate = currentTime;
    }
    
    // Небольшая задержка для стабильности
    delay(100);
}

void connectToWiFi() {
    Serial.println("Connecting to Wi-Fi...");
    WiFi.begin(ssid, password);
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    Serial.println("\nConnected to Wi-Fi");
}

void connectToMQTT() {
    client.setServer(mqttServer, mqttPort);
    client.setCallback(mqttCallback);
    reconnectToMQTT();
}

void reconnectToMQTT() {
    while (!client.connected()) {
        Serial.println("Connecting to MQTT...");
        if (client.connect("ESP32Client", mqttUser, mqttPassword)) {
            Serial.println("Connected to MQTT");
            client.subscribe(topicAlarmTime);
            client.subscribe(topicAlarmStatus);
        } else {
            Serial.print("Failed, rc=");
            Serial.print(client.state());
            Serial.println(" Retrying in 5 seconds...");
            delay(5000);
        }
    }
}

void mqttCallback(char* topic, byte* payload, unsigned int length) {
    String message;
    for (int i = 0; i < length; i++) {
        message += (char)payload[i];
    }
    
    if (String(topic) == topicAlarmTime) {
        unsigned long timeSeconds = message.toInt(); // Время в секундах
        if (timeSeconds > 0) {
            unsigned long currentTime = millis();
            
            // Вычисляем абсолютное время срабатывания будильника
            alarmTime = currentTime + (timeSeconds * 1000);
            
            // Определяем, как долго будет работать свет до срабатывания будильника
            unsigned long lightLeadTime;
            
            if (timeSeconds >= (LIGHT_DURATION / 1000)) {
                // Если осталось более 30 минут - используем полную длительность
                lightLeadTime = LIGHT_DURATION;
            } else if (timeSeconds >= (MIN_LIGHT_DURATION / 1000)) {
                // Если осталось от 5 до 30 минут - используем 80% от оставшегося времени
                lightLeadTime = (timeSeconds * 1000) * 0.8;
            } else {
                // Если осталось меньше 5 минут - используем 50% от оставшегося времени
                lightLeadTime = (timeSeconds * 1000) * 0.5;
            }
            
            // Убеждаемся, что длительность света не превышает время до будильника
            if (lightLeadTime > (timeSeconds * 1000)) {
                lightLeadTime = (timeSeconds * 1000) * 0.8; // Ограничиваем 80% от времени до будильника
            }
            
            // Вычисляем время начала света
            lightTime = currentTime;
            if (timeSeconds * 1000 > lightLeadTime) {
                lightTime = alarmTime - lightLeadTime;
            }
            
            // Активируем будильник
            alarmActive = true;
            lightActive = false;
            buzzerActive = false;
            
            Serial.println("Alarm time set: " + String(alarmTime));
            Serial.println("Current time: " + String(currentTime));
            Serial.println("Time until alarm: " + String(timeSeconds) + " seconds");
            Serial.println("Light will start at: " + String(lightTime));
            Serial.println("Light lead time: " + String(lightLeadTime / 1000) + " seconds");
            Serial.println("Time difference: " + String((lightTime > currentTime) ? 
                          (lightTime - currentTime) / 1000 : 0) + " seconds until light");
        } else if (timeSeconds == 0) {
            // Получена команда отмены будильника
            Serial.println("Received cancel alarm command");
            turnOffAlarm();
        }
    }
}

void updateLight(float progress) {
    // Ограничиваем прогресс от 0 до 1
    progress = constrain(progress, 0.0, 1.0);
    
    // Определяем, сколько светодиодов нужно зажечь (плавная логика)
    int ledsToLight = ceil(progress * NUM_LEDS);
    
    // Также увеличиваем яркость с прогрессом
    int brightness = (int)(200 + (55 * progress)); // Начинаем с минимальной яркости 10
    
    // Красный цвет для рассвета
    int red = brightness;
    // Постепенно добавляем желтый и белый компоненты
    int green = (int)(brightness * (progress * 0.8));
    int blue = (int)(brightness * (progress * 0.5)); 
    
    // Очищаем все светодиоды
    ring.clear();
    
    // Зажигаем нужное количество светодиодов с постепенно увеличивающейся яркостью
    for (int i = 0; i < ledsToLight; i++) {
        ring.setPixelColor(i, ring.Color(red, green, blue));
    }
    
    ring.show();
}

// Обновляем звуковой сигнал будильника (вызывается периодически, когда время будильника наступило)
void updateBuzzer() {
    static unsigned long lastToneChange = 0;
    static bool highTone = false;
    static int volume = 128; // Начинаем с половинной громкости
    
    unsigned long currentTime = millis();
    
    // Меняем тон каждые 500 мс для эффекта сирены
    if (currentTime - lastToneChange > 500) {
        lastToneChange = currentTime;
        highTone = !highTone;
        
        // Увеличиваем громкость постепенно
        if (volume < 255) {
            volume += 5;
        }
        
        // Переключаемся между двумя тонами
        if (highTone) {
            tone(BUZZER_PIN, 2000);
        } else {
            tone(BUZZER_PIN, 1500);
        }
    }
}

void turnOffAlarm() {
    if (alarmActive) {
        Serial.println("Turning off alarm at " + getTimeString());
        
        alarmActive = false;
        lightActive = false;
        buzzerActive = false;
        
        // Останавливаем звук
        noTone(BUZZER_PIN);
        digitalWrite(BUZZER_PIN, LOW);
        
        // Выключаем все светодиоды
        ring.clear();
        ring.show();

        // Отправляем данные о пробуждении, если свет был активен
        if (lightStartTime > 0) {
            wakeTime = millis() - lightStartTime;
            String wakeTimeJson = "{\"wake_duration\":" + String(wakeTime) + "}";
            client.publish(topicWakeTime, wakeTimeJson.c_str());
            Serial.println("Wake time sent: " + wakeTimeJson);
            
            // Сбрасываем время начала света
            lightStartTime = 0;
        }

        // Публикуем статус будильника как отключенный и сработавший
        client.publish(topicAlarmStatus, "{\"active\": false, \"triggered\": true}");
        Serial.println("Alarm turned off at: " + String(millis()));
    }
}

void sendSensorData() {
    TempAndHumidity data = dhtSensor.getTempAndHumidity();
    if (dhtSensor.getStatus() == 0) {
        String payload = "{\"temperature\":" + String(data.temperature, 2) + ",\"humidity\":" + String(data.humidity, 1) + "}";
        client.publish(topicSensorData, payload.c_str());
        Serial.println("Data sent to MQTT: " + payload);
    }
}

// Функция для форматирования времени
String getTimeString() {
    unsigned long currentTime = millis();
    unsigned long seconds = currentTime / 1000;
    unsigned long minutes = seconds / 60;
    unsigned long hours = minutes / 60;
    
    seconds %= 60;
    minutes %= 60;
    hours %= 24;
    
    return String(hours) + ":" + 
           (minutes < 10 ? "0" : "") + String(minutes) + ":" + 
           (seconds < 10 ? "0" : "") + String(seconds);
}