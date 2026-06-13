package com.health.ai.impl;

import com.health.ai.AIProvider;
import com.health.ai.AIServiceAdapter;
import com.health.exception.AIServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class MockAIAdapter implements AIServiceAdapter {

    @Override
    public AIProvider getProvider() {
        return AIProvider.MOCK;
    }

    @Override
    public String generateResponse(String message, String context) throws AIServiceException {
        log.info("使用模拟AI服务处理请求，消息长度: {}", message.length());

        // 检查是否包含健康数据摘要，如果有则解析并给出个性化回复
        if (message.contains("[用户近期健康数据摘要]")) {
            return generatePersonalizedResponse(message);
        }

        // 回退到原有关键词匹配逻辑
        return keywordResponse(message.toLowerCase());
    }

    private String generatePersonalizedResponse(String message) {
        Map<String, HealthStat> stats = parseHealthData(message);

        int questionStart = message.indexOf("[用户问题]");
        String userQuestion = questionStart >= 0 ? message.substring(questionStart + 7).trim() : message;
        userQuestion = userQuestion.toLowerCase();

        StringBuilder sb = new StringBuilder();
        sb.append("根据您过去30天的健康数据，为您进行个性化分析：\n\n");

        // 数据概览
        sb.append("【数据概览】\n");
        for (Map.Entry<String, HealthStat> e : stats.entrySet()) {
            HealthStat s = e.getValue();
            sb.append(String.format("· %s: 最新%.1f%s，平均%.1f%s",
                    s.label, s.latest, s.unit, s.avg, s.unit));
            if (s.anomaly != null) {
                sb.append(" ⚠").append(s.anomaly);
            }
            sb.append("\n");
        }

        // 异常分析
        boolean hasAnomaly = stats.values().stream().anyMatch(s -> s.anomaly != null);
        if (hasAnomaly) {
            sb.append("\n【异常提醒】\n");
            for (Map.Entry<String, HealthStat> e : stats.entrySet()) {
                HealthStat s = e.getValue();
                if (s.anomaly != null) {
                    sb.append("· ").append(s.label).append(": ").append(s.anomaly).append("\n");
                }
            }

            // 针对具体问题的回答
            if (userQuestion.contains("血糖") || userQuestion.contains("糖")) {
                HealthStat bs = stats.get("血糖");
                if (bs != null && bs.anomaly != null) {
                    sb.append("\n【血糖专项分析】\n");
                    sb.append("您近日血糖出现明显异常，最高达").append(String.format("%.1f", bs.max))
                      .append(bs.unit).append("，远超正常范围(3.9-6.1 mmol/L)。\n");
                    sb.append("建议：\n");
                    sb.append("1. 立即就医进行糖耐量检查和HbA1c检测\n");
                    sb.append("2. 控制碳水化合物摄入，避免高GI食物\n");
                    sb.append("3. 增加餐后运动\n");
                    sb.append("4. 每天监测空腹和餐后血糖\n");
                    sb.append("5. 如确诊糖尿病，需按医嘱用药\n");
                }
            } else if (userQuestion.contains("心率") || userQuestion.contains("心脏")) {
                HealthStat hr = stats.get("心率");
                if (hr != null && hr.anomaly != null) {
                    sb.append("\n【心率专项分析】\n");
                    sb.append("您在特定时期心率偏高，最高").append(String.format("%.0f", hr.max))
                      .append("bpm，超出正常静息范围(60-100 bpm)。\n");
                    sb.append("建议注意休息，避免剧烈运动，如持续异常请就医。\n");
                }
            }
        } else {
            sb.append("\n【总体评估】\n您的各项健康指标整体处于正常范围，请继续保持良好的生活习惯。\n");
        }

        // 针对用户具体问题的个性化回答
        if (userQuestion.contains("怎么样") || userQuestion.contains("状况") || userQuestion.contains("健康")) {
            sb.append("\n综合来看，");
            if (hasAnomaly) {
                sb.append("您存在一些需要关注的健康指标，建议针对异常项及时就医检查。");
            } else {
                sb.append("您的健康状况良好，各项指标均在正常范围内。");
            }
        }

        sb.append("\n\n---\n以上分析基于您录入的健康数据，仅供参考，不能替代专业医疗诊断。如有不适请及时就医。");

        return sb.toString();
    }

    private Map<String, HealthStat> parseHealthData(String message) {
        Map<String, HealthStat> stats = new LinkedHashMap<>();

        Pattern p = Pattern.compile(
                "- (\\S+): 最新=([\\d.]+)(\\S+), 平均=([\\d.]+)(\\S+), 最高=([\\d.]+), 最低=([\\d.]+)");

        String[] lines = message.split("\n");
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                String type = m.group(1);
                double latest = Double.parseDouble(m.group(2));
                String unit = m.group(3);
                double avg = Double.parseDouble(m.group(4));
                double max = Double.parseDouble(m.group(6));
                double min = Double.parseDouble(m.group(7));

                HealthStat stat = new HealthStat(type, latest, avg, max, min, unit);

                // 异常判断
                switch (type) {
                    case "步数":
                        if (min < 3000) stat.anomaly = "存在运动量严重不足的时期(最低仅" + (int)min + "步/天)";
                        break;
                    case "心率":
                        if (max > 100) stat.anomaly = "存在心率过快的时期(最高" + (int)max + "bpm)，可能与发烧或紧张有关";
                        break;
                    case "睡眠时长":
                        if (min < 5) stat.anomaly = "存在严重睡眠不足的时期(最低仅" + String.format("%.1f", min) + "小时)";
                        break;
                    case "血压":
                        if (max > 140) stat.anomaly = "存在血压偏高的时期(最高" + (int)max + "mmHg)，需关注心血管风险";
                        break;
                    case "血糖":
                        if (max > 7.0) stat.anomaly = "存在血糖显著升高的时期(最高" + String.format("%.1f", max) + "mmol/L)，需警惕糖尿病风险";
                        break;
                    case "体重":
                        if (Math.abs(max - min) > 2) stat.anomaly = "体重波动较大";
                        break;
                }

                stats.put(type, stat);
            }
        }

        // 如果没有解析到统计数据，从消息中尝试提取键值对
        if (stats.isEmpty()) {
            stats.putAll(parseSimpleFormat(message));
        }

        return stats;
    }

    private Map<String, HealthStat> parseSimpleFormat(String message) {
        Map<String, HealthStat> result = new LinkedHashMap<>();
        // simplify: try to find <name>: <number><unit> patterns
        Pattern p = Pattern.compile("(\\S+):\\s*([\\d.]+)\\s*(\\S*)");
        Matcher m = p.matcher(message);
        while (m.find()) {
            String name = m.group(1);
            double val = Double.parseDouble(m.group(2));
            String unit = m.group(3);
            if (!result.containsKey(name)) {
                result.put(name, new HealthStat(name, val, val, val, val, unit));
            }
        }
        return result;
    }

    private String keywordResponse(String lowerMessage) {
        if (lowerMessage.contains("睡眠")) {
            return "改善睡眠质量的建议：\n1. 保持规律的作息时间\n2. 睡前避免使用电子设备\n3. 创造安静、舒适的睡眠环境\n4. 避免睡前饮用咖啡或茶\n5. 适当进行放松活动，如冥想或深呼吸\n\n如果您有严重的睡眠问题，建议咨询专业医生。";
        } else if (lowerMessage.contains("运动")) {
            return "适合您的运动计划建议：\n1. 每周至少进行150分钟中等强度有氧运动\n2. 每周进行2-3次力量训练\n3. 每天保持适当的身体活动，如步行\n4. 根据个人健康状况选择适合的运动方式\n5. 逐渐增加运动强度，避免过度训练\n\n请根据您的身体状况和医生建议调整运动计划。";
        } else if (lowerMessage.contains("血压")) {
            return "控制血压的建议：\n1. 保持健康的饮食习惯，减少盐的摄入\n2. 定期进行身体活动\n3. 保持健康的体重\n4. 限制酒精摄入\n5. 管理压力\n6. 按照医生建议服用降压药物\n\n请定期监测血压，并咨询医生获取个性化建议。";
        } else if (lowerMessage.contains("饮食")) {
            return "健康饮食建议：\n1. 多吃蔬菜水果，每天摄入5份以上\n2. 选择全谷物食品\n3. 适量摄入蛋白质，如瘦肉、鱼类、豆类\n4. 限制饱和脂肪和反式脂肪的摄入\n5. 控制糖分和盐分的摄入\n6. 保持充足的水分摄入\n\n请根据个人健康状况调整饮食计划。";
        } else if (lowerMessage.contains("压力")) {
            return "减轻压力的建议：\n1. 进行深呼吸和冥想练习\n2. 保持规律的运动\n3. 确保充足的睡眠\n4. 与朋友和家人保持联系\n5. 培养兴趣爱好\n6. 学习时间管理技巧\n7. 寻求专业帮助（如心理咨询）\n\n如果您感到持续的压力或焦虑，建议咨询专业人士。";
        } else if (lowerMessage.contains("心率")) {
            return "心率异常的建议：\n1. 休息并监测心率变化\n2. 避免咖啡因和其他兴奋剂\n3. 保持充足的水分摄入\n4. 避免过度运动\n5. 如果心率持续异常或伴有胸痛、呼吸困难等症状，请立即就医\n\n正常静息心率范围通常为60-100次/分钟，但这可能因年龄和健康状况而异。";
        } else if (lowerMessage.contains("体重")) {
            return "健康体重管理建议：\n1. 保持均衡的饮食，控制热量摄入\n2. 增加身体活动，每天至少30分钟中等强度运动\n3. 保持规律的作息时间\n4. 避免情绪化进食\n5. 设定合理的体重目标，每周减重0.5-1公斤为宜\n6. 定期监测体重变化\n\n请根据个人健康状况调整体重管理计划，如有需要咨询医生或营养师。";
        } else if (lowerMessage.contains("血糖")) {
            return "血糖管理建议：\n1. 控制碳水化合物的摄入，选择低GI食物\n2. 保持规律的饮食时间\n3. 适量进行有氧运动\n4. 按照医生建议监测血糖水平\n5. 遵循医生的药物治疗方案\n6. 保持健康的体重\n\n如果您有糖尿病或血糖异常，请定期咨询医生获取个性化建议。";
        } else if (lowerMessage.contains("你好") || lowerMessage.contains("嗨") || lowerMessage.contains("hello")) {
            return "你好！我是您的智能健康助手。有什么健康问题可以帮您解答吗？";
        } else if (lowerMessage.contains("谢谢") || lowerMessage.contains("thank")) {
            return "不客气！如果您有任何其他健康问题，随时可以问我。";
        }
        return "感谢您的咨询。作为智能健康助手，我可以回答关于睡眠、运动、饮食、压力管理、血压、心率、体重和血糖等健康问题。请问您有什么具体的健康问题需要了解？\n\n免责声明：我的建议仅供参考，不能替代专业医疗建议。如有健康问题，请咨询专业医生。";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void generateStreamResponse(String message, String context, Consumer<String> chunkCallback) throws AIServiceException {
        log.info("使用模拟AI服务处理流式请求");
        
        String fullResponse = generateResponse(message, context);
        
        // 模拟流式输出，逐段发送
        int chunkSize = 50;
        for (int i = 0; i < fullResponse.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, fullResponse.length());
            String chunk = fullResponse.substring(i, end);
            chunkCallback.accept(chunk);
            
            // 模拟网络延迟，使流式效果更明显
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static class HealthStat {
        String label;
        double latest, avg, max, min;
        String unit;
        String anomaly;

        HealthStat(String label, double latest, double avg, double max, double min, String unit) {
            this.label = label;
            this.latest = latest;
            this.avg = avg;
            this.max = max;
            this.min = min;
            this.unit = unit;
        }
    }
}
