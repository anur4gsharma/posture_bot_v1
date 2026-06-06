/*
 * ESP32-CAM MJPEG Streamer
 * ========================
 * Connects to WiFi and streams camera frames as MJPEG
 * at http://<IP>:81/stream
 *
 * The Python posture detection script reads this stream URL.
 *
 * Board: "AI Thinker ESP32-CAM"
 * Partition Scheme: "Huge APP (3MB No OTA/1MB SPIFFS)"
 *
 * IMPORTANT: Change ssid and password to your WiFi credentials.
 */

#include "esp_camera.h"
#include <WiFi.h>
#include "esp_http_server.h"

// ======================== WIFI CONFIG ========================
const char* ssid     = "YOUR_WIFI_SSID";      // <-- CHANGE THIS
const char* password = "YOUR_WIFI_PASSWORD";   // <-- CHANGE THIS
// =============================================================

// ======================== CAMERA PINS (AI-Thinker) ========================
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// Flash LED (built-in on GPIO 4)
#define FLASH_GPIO_NUM     4
// ==========================================================================

httpd_handle_t stream_httpd = NULL;

#define PART_BOUNDARY "123456789000000000000987654321"
static const char* _STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;
static const char* _STREAM_BOUNDARY = "\r\n--" PART_BOUNDARY "\r\n";
static const char* _STREAM_PART = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";


// MJPEG stream handler
static esp_err_t stream_handler(httpd_req_t *req) {
  camera_fb_t *fb = NULL;
  esp_err_t res = ESP_OK;
  char part_buf[64];

  res = httpd_resp_set_type(req, _STREAM_CONTENT_TYPE);
  if (res != ESP_OK) return res;

  while (true) {
    fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println("Camera capture failed");
      res = ESP_FAIL;
    } else {
      size_t hlen = snprintf(part_buf, 64, _STREAM_PART, fb->len);
      res = httpd_resp_send_chunk(req, _STREAM_BOUNDARY, strlen(_STREAM_BOUNDARY));
      if (res == ESP_OK) {
        res = httpd_resp_send_chunk(req, part_buf, hlen);
      }
      if (res == ESP_OK) {
        res = httpd_resp_send_chunk(req, (const char *)fb->buf, fb->len);
      }
      esp_camera_fb_return(fb);
    }
    if (res != ESP_OK) break;
  }
  return res;
}


void startStreamServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.server_port = 81;

  httpd_uri_t stream_uri = {
    .uri       = "/stream",
    .method    = HTTP_GET,
    .handler   = stream_handler,
    .user_ctx  = NULL
  };

  if (httpd_start(&stream_httpd, &config) == ESP_OK) {
    httpd_register_uri_handler(stream_httpd, &stream_uri);
    Serial.println("Stream server started on port 81");
  }
}


void setup() {
  Serial.begin(115200);
  Serial.println();
  Serial.println("================================");
  Serial.println("ESP32-CAM MJPEG Streamer");
  Serial.println("================================");

  // Camera configuration
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0       = Y2_GPIO_NUM;
  config.pin_d1       = Y3_GPIO_NUM;
  config.pin_d2       = Y4_GPIO_NUM;
  config.pin_d3       = Y5_GPIO_NUM;
  config.pin_d4       = Y6_GPIO_NUM;
  config.pin_d5       = Y7_GPIO_NUM;
  config.pin_d6       = Y8_GPIO_NUM;
  config.pin_d7       = Y9_GPIO_NUM;
  config.pin_xclk     = XCLK_GPIO_NUM;
  config.pin_pclk     = PCLK_GPIO_NUM;
  config.pin_vsync    = VSYNC_GPIO_NUM;
  config.pin_href     = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn     = PWDN_GPIO_NUM;
  config.pin_reset    = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;

  // Use QVGA for good speed, or VGA for better quality
  if (psramFound()) {
    config.frame_size   = FRAMESIZE_VGA;      // 640x480
    config.jpeg_quality = 12;                  // 0-63, lower = better
    config.fb_count     = 2;
    Serial.println("PSRAM found — using VGA resolution");
  } else {
    config.frame_size   = FRAMESIZE_QVGA;     // 320x240
    config.jpeg_quality = 15;
    config.fb_count     = 1;
    Serial.println("No PSRAM — using QVGA resolution");
  }

  // Init camera
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed with error 0x%x\n", err);
    delay(1000);
    ESP.restart();
  }
  Serial.println("Camera initialized successfully");

  // Adjust camera settings for better image
  sensor_t *s = esp_camera_sensor_get();
  s->set_brightness(s, 1);     // Slightly brighter
  s->set_contrast(s, 1);       // Slightly more contrast
  s->set_vflip(s, 0);          // Set to 1 if image is upside down
  s->set_hmirror(s, 0);        // Set to 1 if image is mirrored

  // Connect to WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.println("================================");
    Serial.println("WiFi connected!");
    Serial.print("IP Address: ");
    Serial.println(WiFi.localIP());
    Serial.println();
    Serial.print("Stream URL: http://");
    Serial.print(WiFi.localIP());
    Serial.println(":81/stream");
    Serial.println("================================");
    Serial.println();
    Serial.println("Use this URL in esp32_posture.py:");
    Serial.print("  ESP32_CAM_URL = \"http://");
    Serial.print(WiFi.localIP());
    Serial.println(":81/stream\"");
    Serial.println();
  } else {
    Serial.println();
    Serial.println("WiFi connection FAILED!");
    Serial.println("Check SSID and password, then restart.");
    return;
  }

  // Start the MJPEG stream server
  startStreamServer();
}


void loop() {
  // Nothing to do here — stream is handled by HTTP server
  delay(10000);

  // Print status periodically
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("Uptime: %lu s | Free heap: %u bytes | IP: %s\n",
                  millis() / 1000, ESP.getFreeHeap(),
                  WiFi.localIP().toString().c_str());
  } else {
    Serial.println("WiFi disconnected! Reconnecting...");
    WiFi.reconnect();
  }
}
