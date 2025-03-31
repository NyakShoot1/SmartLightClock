#include <WiFi.h>
#include <PubSubClient.h>
#include <Adafruit_NeoPixel.h>
#include "DHTesp.h"

// Wi-Fi settings
const char* ssid = "Wokwi-GUEST";
const char* password = "";

// MQTT settings
const char* mqttServer = "192.168.3.7";
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

unsigned long alarmTime = 0;
bool alarmActive = false;
bool buzzerActive = false;
unsigned long lightStartTime = 0;
unsigned long wakeTime = 0;
const unsigned long lightDuration = 20000; // 30 * 60 * 1000; // 30 минут в миллисекундах

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
    if (!client.connected()) {
        reconnectToMQTT();
    }
    client.loop();

    if (alarmActive && millis() >= alarmTime) {
        if (lightStartTime == 0) {
            lightStartTime = millis();
        }
        if (millis() - lightStartTime <= lightDuration) {
            gradualLightOn();
        } else if (!buzzerActive) {
            digitalWrite(BUZZER_PIN, HIGH);
            buzzerActive = true;
            Serial.println("Buzzer activated");
        }
    }
    
    if (digitalRead(BUTTON_PIN) == LOW) {
        turnOffAlarm();
    }

    sendSensorData();
    delay(2000);
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
        unsigned long time = message.toInt() * 1000;
        if (time > 0) {
            alarmTime = millis() + time;
            alarmActive = true;
            lightStartTime = 0;
            buzzerActive = false;
            Serial.println("Alarm set for: " + String(alarmTime));
        }
    }
}

void gradualLightOn() {
    unsigned long elapsedTime = millis() - lightStartTime;
    float progress = (float)elapsedTime / lightDuration;
    int ledsToLight = (int)(progress * NUM_LEDS);
    for (int i = 0; i < ledsToLight; i++) {
        ring.setPixelColor(i, ring.Color(255, 255, 255));
    }
    ring.show();
}

void turnOffAlarm() {
  alarmActive = false;
  buzzerActive = false;
  digitalWrite(BUZZER_PIN, LOW);
  ring.clear();
  ring.show();

  if (lightStartTime > 0) {
      wakeTime = millis() - lightStartTime;
      String wakeTimeJson = "{\"wake_time\":" + String(wakeTime) + "}";
      client.publish(topicWakeTime, wakeTimeJson.c_str());
      Serial.println("Wake time sent: " + wakeTimeJson);
  }

  client.publish(topicAlarmStatus, "{\"status\":\"off\"}");
  Serial.println("Alarm turned off");
}


void sendSensorData() {
    TempAndHumidity data = dhtSensor.getTempAndHumidity();
    if (dhtSensor.getStatus() == 0) {
        String payload = "{\"temperature\":" + String(data.temperature, 2) + ",\"humidity\":" + String(data.humidity, 1) + "}";
        client.publish(topicSensorData, payload.c_str());
        Serial.println("Data sent to MQTT: " + payload);
    }
}