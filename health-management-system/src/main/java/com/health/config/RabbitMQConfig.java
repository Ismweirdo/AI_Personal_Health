package com.health.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 * 配置消息队列、交换机和绑定关系
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 消息转换器
     * 使用Jackson进行JSON序列化
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        
        // 开启发送确认
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                System.out.println("消息发送成功");
            } else {
                System.err.println("消息发送失败: " + cause);
            }
        });
        
        // 开启返回确认
        rabbitTemplate.setReturnsCallback(returned -> {
            System.err.println("消息被退回: " + returned.getMessage());
        });
        
        return rabbitTemplate;
    }

    // ==================== AI服务队列 ====================
    
    /**
     * AI请求队列
     */
    @Bean
    public Queue aiRequestQueue() {
        return QueueBuilder.durable("ai.request.queue")
                .withArgument("x-max-length", 1000)  // 最大消息数
                .withArgument("x-message-ttl", 300000)  // 消息TTL 5分钟
                .build();
    }

    /**
     * AI响应队列
     */
    @Bean
    public Queue aiResponseQueue() {
        return QueueBuilder.durable("ai.response.queue")
                .withArgument("x-max-length", 1000)
                .withArgument("x-message-ttl", 300000)
                .build();
    }

    /**
     * AI交换机
     */
    @Bean
    public DirectExchange aiExchange() {
        return new DirectExchange("ai.exchange", true, false);
    }

    /**
     * AI请求绑定
     */
    @Bean
    public Binding aiRequestBinding() {
        return BindingBuilder.bind(aiRequestQueue())
                .to(aiExchange())
                .with("ai.request");
    }

    /**
     * AI响应绑定
     */
    @Bean
    public Binding aiResponseBinding() {
        return BindingBuilder.bind(aiResponseQueue())
                .to(aiExchange())
                .with("ai.response");
    }

    // ==================== 数据导入队列 ====================
    
    /**
     * 数据导入队列
     */
    @Bean
    public Queue dataImportQueue() {
        return QueueBuilder.durable("data.import.queue")
                .withArgument("x-max-length", 100)
                .withArgument("x-message-ttl", 3600000)  // 消息TTL 1小时
                .build();
    }

    /**
     * 数据导入交换机
     */
    @Bean
    public DirectExchange dataImportExchange() {
        return new DirectExchange("data.import.exchange", true, false);
    }

    /**
     * 数据导入绑定
     */
    @Bean
    public Binding dataImportBinding() {
        return BindingBuilder.bind(dataImportQueue())
                .to(dataImportExchange())
                .with("data.import");
    }

    // ==================== 设备数据队列 ====================
    
    /**
     * 设备数据处理队列
     */
    @Bean
    public Queue deviceDataQueue() {
        return QueueBuilder.durable("device.data.queue")
                .withArgument("x-max-length", 10000)
                .withArgument("x-message-ttl", 60000)  // 消息TTL 1分钟
                .build();
    }

    /**
     * 设备数据交换机
     */
    @Bean
    public TopicExchange deviceDataExchange() {
        return new TopicExchange("device.data.exchange", true, false);
    }

    /**
     * 设备数据绑定
     */
    @Bean
    public Binding deviceDataBinding() {
        return BindingBuilder.bind(deviceDataQueue())
                .to(deviceDataExchange())
                .with("device.data.*");
    }

    // ==================== 通知队列 ====================
    
    /**
     * 通知队列
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable("notification.queue")
                .withArgument("x-max-length", 5000)
                .withArgument("x-message-ttl", 86400000)  // 消息TTL 24小时
                .build();
    }

    /**
     * 通知交换机
     */
    @Bean
    public FanoutExchange notificationExchange() {
        return new FanoutExchange("notification.exchange", true, false);
    }

    /**
     * 通知绑定
     */
    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(notificationExchange());
    }

    // ==================== 死信队列 ====================
    
    /**
     * 死信队列
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("dead.letter.queue")
                .build();
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange("dead.letter.exchange", true, false);
    }

    /**
     * 死信绑定
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dead.letter");
    }
}