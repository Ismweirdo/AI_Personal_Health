<template>
  <div class="api-guide-container">
    <div class="guide-header">
      <h1>健康设备接口使用指南</h1>
      <p class="subtitle">Health Device API Documentation</p>
    </div>

    <div class="guide-content">
      <!-- 概述 -->
      <section class="guide-section">
        <h2>1. 概述</h2>
        <p>本系统提供完整的健康设备数据接入接口，支持各种健康设备（如智能手环、血压计、血糖仪等）将健康数据自动同步到健康管理平台。</p>

        <div class="info-box">
          <h3>接口特点</h3>
          <ul>
            <li>✅ RESTful API 设计，遵循行业标准</li>
            <li>✅ API 密钥认证，确保数据安全</li>
            <li>✅ 批量数据写入，提高传输效率</li>
            <li>✅ 完整的数据日志记录和错误处理</li>
            <li>✅ 支持多种健康数据类型</li>
          </ul>
        </div>
      </section>

      <!-- 认证方式 -->
      <section class="guide-section">
        <h2>2. 认证方式</h2>
        <p>设备接口使用 API 密钥进行认证，确保只有授权设备才能写入数据。</p>

        <div class="code-block">
          <h4>获取 API 密钥</h4>
          <p>首先需要注册设备，获取设备 ID 和 API 密钥：</p>
          <pre><code>POST /api/device/register
Content-Type: application/json
Authorization: Bearer {user_token}

{
  "deviceName": "小米手环8",
  "deviceType": "fitness_tracker",
  "deviceModel": "Xiaomi Band 8",
  "manufacturer": "Xiaomi",
  "firmwareVersion": "1.0.0",
  "description": "个人健康追踪设备"
}</code></pre>
        </div>

        <div class="response-block success-response">
          <h4>响应示例</h4>
          <pre><code>{
  "code": 200,
  "message": "success",
  "data": {
    "deviceId": "DEV-ABC123DEF456",
    "apiKey": "HMS-1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P",
    "deviceName": "小米手环8",
    "deviceType": "fitness_tracker",
    "deviceModel": "Xiaomi Band 8",
    "manufacturer": "Xiaomi",
    "firmwareVersion": "1.0.0",
    "status": "active",
    "createdAt": "2026-06-11T10:30:00",
    "message": "设备注册成功"
  }
}</code></pre>
        </div>
      </section>

      <!-- 数据写入接口 -->
      <section class="guide-section">
        <h2>3. 数据写入接口</h2>
        <p>设备通过此接口将健康数据写入系统，支持批量写入多个数据点。</p>

        <div class="code-block">
          <h4>接口地址</h4>
          <pre><code>POST /api/device/data</code></pre>

          <h4>请求头</h4>
          <pre><code>Content-Type: application/json</code></pre>

          <h4>请求参数</h4>
          <table class="api-table">
            <thead>
              <tr>
                <th>参数名</th>
                <th>类型</th>
                <th>必填</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>deviceId</td>
                <td>String</td>
                <td>是</td>
                <td>设备ID，注册时获得</td>
              </tr>
              <tr>
                <td>apiKey</td>
                <td>String</td>
                <td>是</td>
                <td>API密钥，注册时获得</td>
              </tr>
              <tr>
                <td>data</td>
                <td>Array</td>
                <td>是</td>
                <td>健康数据点数组</td>
              </tr>
            </tbody>
          </table>

          <h4>数据点参数</h4>
          <table class="api-table">
            <thead>
              <tr>
                <th>参数名</th>
                <th>类型</th>
                <th>必填</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>type</td>
                <td>String</td>
                <td>是</td>
                <td>数据类型：steps/heart_rate/sleep/weight/blood_pressure/blood_sugar</td>
              </tr>
              <tr>
                <td>value</td>
                <td>Number</td>
                <td>是</td>
                <td>数据值</td>
              </tr>
              <tr>
                <td>unit</td>
                <td>String</td>
                <td>否</td>
                <td>单位（如：步/bpm/小时/kg/mmHg/mmol/L）</td>
              </tr>
              <tr>
                <td>recordDate</td>
                <td>String</td>
                <td>是</td>
                <td>记录时间，格式：yyyy-MM-dd HH:mm:ss</td>
              </tr>
              <tr>
                <td>notes</td>
                <td>String</td>
                <td>否</td>
                <td>备注信息</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="code-block">
          <h4>请求示例</h4>
          <pre><code>{
  "deviceId": "DEV-ABC123DEF456",
  "apiKey": "HMS-1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P",
  "data": [
    {
      "type": "steps",
      "value": 8542,
      "unit": "步",
      "recordDate": "2026-06-11 08:30:00",
      "notes": "晨跑数据"
    },
    {
      "type": "heart_rate",
      "value": 72,
      "unit": "bpm",
      "recordDate": "2026-06-11 08:30:00"
    },
    {
      "type": "sleep",
      "value": 7.5,
      "unit": "小时",
      "recordDate": "2026-06-11 07:00:00",
      "notes": "睡眠质量良好"
    }
  ]
}</code></pre>
        </div>

        <div class="response-block success-response">
          <h4>响应示例</h4>
          <pre><code>{
  "code": 200,
  "message": "success",
  "data": {
    "requestId": "REQ-UUID-12345",
    "deviceId": "DEV-ABC123DEF456",
    "deviceName": "小米手环8",
    "receivedAt": "2026-06-11T10:35:00",
    "totalCount": 3,
    "successCount": 3,
    "failureCount": 0,
    "results": [
      {
        "type": "steps",
        "value": 8542,
        "unit": "步",
        "recordDate": "2026-06-11 08:30:00",
        "success": true,
        "healthDataId": 12345
      },
      {
        "type": "heart_rate",
        "value": 72,
        "unit": "bpm",
        "recordDate": "2026-06-11 08:30:00",
        "success": true,
        "healthDataId": 12346
      },
      {
        "type": "sleep",
        "value": 7.5,
        "unit": "小时",
        "recordDate": "2026-06-11 07:00:00",
        "success": true,
        "healthDataId": 12347
      }
    ],
    "message": "数据接收完成"
  }
}</code></pre>
        </div>
      </section>

      <!-- 支持的数据类型 -->
      <section class="guide-section">
        <h2>4. 支持的数据类型</h2>
        <div class="data-types-grid">
          <div class="data-type-card">
            <h3>步数 (steps)</h3>
            <p><strong>单位：</strong>步</p>
            <p><strong>正常范围：</strong>5000-12000</p>
            <p><strong>示例值：</strong>8542</p>
          </div>
          <div class="data-type-card">
            <h3>心率 (heart_rate)</h3>
            <p><strong>单位：</strong>bpm</p>
            <p><strong>正常范围：</strong>60-100</p>
            <p><strong>示例值：</strong>72</p>
          </div>
          <div class="data-type-card">
            <h3>睡眠 (sleep)</h3>
            <p><strong>单位：</strong>小时</p>
            <p><strong>正常范围：</strong>6-9</p>
            <p><strong>示例值：</strong>7.5</p>
          </div>
          <div class="data-type-card">
            <h3>体重 (weight)</h3>
            <p><strong>单位：</strong>kg</p>
            <p><strong>正常范围：</strong>根据身高BMI计算</p>
            <p><strong>示例值：</strong>65.5</p>
          </div>
          <div class="data-type-card">
            <h3>血压 (blood_pressure)</h3>
            <p><strong>单位：</strong>mmHg</p>
            <p><strong>正常范围：</strong>90-140</p>
            <p><strong>示例值：</strong>120</p>
          </div>
          <div class="data-type-card">
            <h3>血糖 (blood_sugar)</h3>
            <p><strong>单位：</strong>mmol/L</p>
            <p><strong>正常范围：</strong>3.9-7.0</p>
            <p><strong>示例值：</strong>5.4</p>
          </div>
        </div>
      </section>

      <!-- 错误处理 -->
      <section class="guide-section">
        <h2>5. 错误处理</h2>
        <div class="error-codes">
          <div class="error-code-item">
            <h4>400 Bad Request</h4>
            <p>请求参数错误或缺失必填参数</p>
          </div>
          <div class="error-code-item">
            <h4>401 Unauthorized</h4>
            <p>API密钥无效或已过期</p>
          </div>
          <div class="error-code-item">
            <h4>403 Forbidden</h4>
            <p>设备状态异常（未激活或已禁用）</p>
          </div>
          <div class="error-code-item">
            <h4>404 Not Found</h4>
            <p>设备不存在</p>
          </div>
          <div class="error-code-item">
            <h4>422 Unprocessable Entity</h4>
            <p>数据格式错误或数据类型不支持</p>
          </div>
          <div class="error-code-item">
            <h4>500 Internal Server Error</h4>
            <p>服务器内部错误</p>
          </div>
        </div>

        <div class="response-block error-response">
          <h4>错误响应示例</h4>
          <pre><code>{
  "code": 401,
  "message": "无效的API密钥",
  "data": null
}</code></pre>
        </div>
      </section>

      <!-- 最佳实践 -->
      <section class="guide-section">
        <h2>6. 最佳实践</h2>
        <div class="best-practices">
          <div class="practice-item">
            <h3>🔒 安全性</h3>
            <ul>
              <li>妥善保管 API 密钥，不要在客户端代码中暴露</li>
              <li>使用 HTTPS 协议进行数据传输</li>
              <li>定期更换 API 密钥</li>
              <li>监控异常访问行为</li>
            </ul>
          </div>
          <div class="practice-item">
            <h3>⚡ 性能优化</h3>
            <ul>
              <li>使用批量写入接口，减少请求次数</li>
              <li>合理安排数据同步频率</li>
              <li>处理网络异常，实现重试机制</li>
              <li>压缩大数据包</li>
            </ul>
          </div>
          <div class="practice-item">
            <h3>📊 数据质量</h3>
            <ul>
              <li>确保数据时间格式正确</li>
              <li>验证数据范围合理性</li>
              <li>添加有意义的备注信息</li>
              <li>处理异常数据值</li>
            </ul>
          </div>
        </div>
      </section>

      <!-- 代码示例 -->
      <section class="guide-section">
        <h2>7. 代码示例</h2>

        <div class="code-block">
          <h4>Python 示例</h4>
          <pre><code>import requests
import json
from datetime import datetime

# 设备信息
DEVICE_ID = "DEV-ABC123DEF456"
API_KEY = "HMS-1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P"
API_URL = "http://localhost:8080/api/device/data"

# 准备健康数据
health_data = {
    "deviceId": DEVICE_ID,
    "apiKey": API_KEY,
    "data": [
        {
            "type": "steps",
            "value": 8542,
            "unit": "步",
            "recordDate": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "notes": "晨跑数据"
        },
        {
            "type": "heart_rate",
            "value": 72,
            "unit": "bpm",
            "recordDate": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }
    ]
}

# 发送数据
try:
    response = requests.post(
        API_URL,
        json=health_data,
        headers={"Content-Type": "application/json"}
    )
    
    if response.status_code == 200:
        result = response.json()
        print(f"数据发送成功: {result['data']['successCount']}/{result['data']['totalCount']}")
    else:
        print(f"数据发送失败: {response.status_code}")
        print(response.json())
        
except Exception as e:
    print(f"请求异常: {str(e)}")</code></pre>
        </div>

        <div class="code-block">
          <h4>JavaScript 示例</h4>
          <pre><code>// 设备信息
const DEVICE_ID = "DEV-ABC123DEF456";
const API_KEY = "HMS-1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P";
const API_URL = "http://localhost:8080/api/device/data";

// 准备健康数据
const healthData = {
  deviceId: DEVICE_ID,
  apiKey: API_KEY,
  data: [
    {
      type: "steps",
      value: 8542,
      unit: "步",
      recordDate: new Date().toISOString().slice(0, 19).replace('T', ' '),
      notes: "晨跑数据"
    },
    {
      type: "heart_rate",
      value: 72,
      unit: "bpm",
      recordDate: new Date().toISOString().slice(0, 19).replace('T', ' ')
    }
  ]
};

// 发送数据
fetch(API_URL, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify(healthData)
})
  .then(response => response.json())
  .then(result => {
    if (result.code === 200) {
      console.log(`数据发送成功: ${result.data.successCount}/${result.data.totalCount}`);
    } else {
      console.error('数据发送失败:', result.message);
    }
  })
  .catch(error => {
    console.error('请求异常:', error);
  });</code></pre>
        </div>
      </section>

      <!-- 常见问题 -->
      <section class="guide-section">
        <h2>8. 常见问题</h2>
        <div class="faq-item">
          <h3>Q: 如何获取 API 密钥？</h3>
          <p>A: 需要先注册设备，通过 /api/device/register 接口注册后会返回设备ID和API密钥。</p>
        </div>
        <div class="faq-item">
          <h3>Q: API 密钥会过期吗？</h3>
          <p>A: 目前 API 密钥不会自动过期，但建议定期更换以保证安全性。</p>
        </div>
        <div class="faq-item">
          <h3>Q: 支持哪些数据类型？</h3>
          <p>A: 目前支持步数、心率、睡眠、体重、血压、血糖六种数据类型。</p>
        </div>
        <div class="faq-item">
          <h3>Q: 批量写入有限制吗？</h3>
          <p>A: 单次请求建议不超过100个数据点，以确保传输稳定性。</p>
        </div>
        <div class="faq-item">
          <h3>Q: 数据写入失败怎么办？</h3>
          <p>A: 检查响应中的错误信息，确认API密钥、设备状态、数据格式是否正确，必要时联系技术支持。</p>
        </div>
      </section>

      <!-- 技术支持 -->
      <section class="guide-section">
        <h2>9. 技术支持</h2>
        <div class="support-info">
          <p>如有任何问题或建议，请通过以下方式联系我们：</p>
          <ul>
            <li>📧 Email: support@healthmanagement.com</li>
            <li>📱 电话: 400-123-4567</li>
            <li>💬 在线客服: 工作日 9:00-18:00</li>
            <li>📚 文档中心: <a href="#" target="_blank">https://docs.healthmanagement.com</a></li>
          </ul>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
// API指南页面逻辑
</script>

<style scoped>
.api-guide-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px;
  background: linear-gradient(135deg, #FB7299 0%, #00A1D6 100%);
  min-height: 100vh;
}

.guide-header {
  text-align: center;
  color: white;
  margin-bottom: 40px;
}

.guide-header h1 {
  font-size: 2.5rem;
  margin-bottom: 10px;
  font-weight: 700;
}

.subtitle {
  font-size: 1.2rem;
  opacity: 0.9;
}

.guide-content {
  background: white;
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.guide-section {
  margin-bottom: 50px;
}

.guide-section h2 {
  color: #FB7299;
  font-size: 1.8rem;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 3px solid #FB7299;
}

.guide-section h3 {
  color: #333;
  font-size: 1.3rem;
  margin-bottom: 15px;
}

.guide-section h4 {
  color: #555;
  font-size: 1.1rem;
  margin-bottom: 10px;
  margin-top: 20px;
}

.guide-section p {
  color: #666;
  line-height: 1.8;
  margin-bottom: 15px;
}

.info-box {
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  padding: 25px;
  border-radius: 10px;
  margin: 20px 0;
}

.info-box h3 {
  color: #FB7299;
  margin-bottom: 15px;
}

.info-box ul {
  list-style: none;
  padding: 0;
}

.info-box li {
  padding: 8px 0;
  color: #555;
  font-size: 1.05rem;
}

.code-block {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 25px;
  border-radius: 10px;
  margin: 20px 0;
  overflow-x: auto;
}

.code-block h4 {
  color: #4ec9b0;
  margin-top: 0;
}

.code-block pre {
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 0.9rem;
  line-height: 1.6;
}

.code-block code {
  color: #d4d4d4;
}

.response-block {
  padding: 20px;
  border-radius: 12px;
  margin: 20px 0;
  border-left: 4px solid transparent;
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
}

.response-block h4 {
  margin-top: 0;
  margin-bottom: 12px;
  font-weight: 700;
}

.response-block pre {
  margin: 0;
  padding: 18px 20px;
  border-radius: 10px;
  overflow-x: auto;
  font-family: 'Courier New', monospace;
  font-size: 0.95rem;
  line-height: 1.75;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.response-block code {
  font-family: inherit;
  font-weight: 600;
  letter-spacing: 0.01em;
}

.success-response {
  background: linear-gradient(135deg, #ecfeff 0%, #dbeafe 100%);
  border-left-color: #0284c7;
}

.success-response h4 {
  color: #075985;
}

.success-response pre {
  background: linear-gradient(135deg, #0f172a 0%, #082f49 100%);
  color: #f8fafc;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.success-response code {
  color: #e0f2fe;
  text-shadow: 0 0 1px rgba(224, 242, 254, 0.2);
}

.error-response {
  background: linear-gradient(135deg, #fef2f2 0%, #ffedd5 100%);
  border-left-color: #dc2626;
}

.error-response h4 {
  color: #b91c1c;
}

.error-response pre {
  background: linear-gradient(135deg, #1f2937 0%, #7f1d1d 100%);
  color: #fff7ed;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05);
}

.error-response code {
  color: #ffe4e6;
  text-shadow: 0 0 1px rgba(255, 228, 230, 0.18);
}

.api-table {
  width: 100%;
  border-collapse: collapse;
  margin: 20px 0;
  background: white;
}

.api-table th,
.api-table td {
  border: 1px solid #e5e7eb;
  padding: 12px;
  text-align: left;
}

.api-table th {
  background: #FB7299;
  color: white;
  font-weight: 600;
}

.api-table tr:nth-child(even) {
  background: #f9fafb;
}

.data-types-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin: 20px 0;
}

.data-type-card {
  background: linear-gradient(135deg, #FB7299 0%, #00A1D6 100%);
  color: white;
  padding: 25px;
  border-radius: 15px;
  box-shadow: 0 10px 30px rgba(102, 126, 234, 0.3);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.data-type-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 15px 40px rgba(102, 126, 234, 0.4);
}

.data-type-card h3 {
  color: white;
  margin-bottom: 15px;
  font-size: 1.4rem;
}

.data-type-card p {
  color: rgba(255, 255, 255, 0.9);
  margin: 8px 0;
}

.error-codes {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 15px;
  margin: 20px 0;
}

.error-code-item {
  background: #fef2f2;
  border-left: 4px solid #ef4444;
  padding: 15px;
  border-radius: 5px;
}

.error-code-item h4 {
  color: #ef4444;
  margin: 0 0 8px 0;
  font-size: 1rem;
}

.error-code-item p {
  color: #7f1d1d;
  margin: 0;
  font-size: 0.9rem;
}

.best-practices {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin: 20px 0;
}

.practice-item {
  background: #f0fdf4;
  border: 2px solid #22c55e;
  padding: 20px;
  border-radius: 10px;
}

.practice-item h3 {
  color: #15803d;
  margin-bottom: 15px;
}

.practice-item ul {
  list-style: none;
  padding: 0;
}

.practice-item li {
  padding: 6px 0;
  color: #166534;
  position: relative;
  padding-left: 20px;
}

.practice-item li:before {
  content: "✓";
  position: absolute;
  left: 0;
  color: #22c55e;
  font-weight: bold;
}

.faq-item {
  background: #fefce8;
  border-left: 4px solid #eab308;
  padding: 20px;
  margin: 15px 0;
  border-radius: 5px;
}

.faq-item h3 {
  color: #a16207;
  margin: 0 0 10px 0;
  font-size: 1.1rem;
}

.faq-item p {
  color: #713f12;
  margin: 0;
}

.support-info {
  background: linear-gradient(135deg, #FB7299 0%, #00A1D6 100%);
  color: white;
  padding: 30px;
  border-radius: 15px;
  text-align: center;
}

.support-info p {
  color: white;
  font-size: 1.2rem;
  margin-bottom: 20px;
}

.support-info ul {
  list-style: none;
  padding: 0;
  display: inline-block;
  text-align: left;
}

.support-info li {
  padding: 10px 0;
  color: rgba(255, 255, 255, 0.9);
  font-size: 1.1rem;
}

.support-info a {
  color: #fbbf24;
  text-decoration: underline;
}

@media (max-width: 768px) {
  .api-guide-container {
    padding: 20px 10px;
  }

  .guide-content {
    padding: 20px;
  }

  .guide-header h1 {
    font-size: 1.8rem;
  }

  .subtitle {
    font-size: 1rem;
  }

  .guide-section h2 {
    font-size: 1.4rem;
  }

  .data-types-grid,
  .error-codes,
  .best-practices {
    grid-template-columns: 1fr;
  }
}
</style>
