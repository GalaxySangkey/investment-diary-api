package com.investmentdiary.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 메시지 큐 설정
 * 환경 변수로 RabbitMQ 또는 Redis 선택
 * RabbitMQ host가 비어있으면 자동 구성을 비활성화
 */
@Configuration
@EnableScheduling // 스케줄링 활성화 (QueueConsumer에서 사용)
public class QueueConfig {
    
    @Value("${queue.type:memory}")
    private String queueType;
    
    @Value("${queue.name:api_requests}")
    private String queueName;
    
    @Value("${queue.durable:true}")
    private boolean queueDurable;
    
    // RabbitMQ 설정
    @Value("${spring.rabbitmq.host:}")
    private String rabbitmqHost;
    
    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitmqPort;
    
    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitmqUsername;
    
    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitmqPassword;
    
    @Value("${spring.rabbitmq.virtual-host:/}")
    private String rabbitmqVirtualHost;
    
    /**
     * RabbitMQ Connection Factory
     * QUEUE_TYPE=rabbitmq이고 RABBITMQ_HOST가 설정되어 있을 때만 생성
     */
    @Bean
    @ConditionalOnProperty(name = "queue.type", havingValue = "rabbitmq")
    public ConnectionFactory rabbitConnectionFactory() {
        String host = System.getenv("RABBITMQ_HOST");
        if (host == null || host.isEmpty()) {
            host = rabbitmqHost;
        }
        
        // RabbitMQ host가 비어있으면 null 반환 (자동 구성 비활성화)
        if (host == null || host.isEmpty()) {
            return null;
        }
        
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(host);
        factory.setPort(rabbitmqPort);
        factory.setUsername(System.getenv("RABBITMQ_USERNAME") != null ? 
            System.getenv("RABBITMQ_USERNAME") : rabbitmqUsername);
        factory.setPassword(System.getenv("RABBITMQ_PASSWORD") != null ? 
            System.getenv("RABBITMQ_PASSWORD") : rabbitmqPassword);
        factory.setVirtualHost(rabbitmqVirtualHost);
        
        return factory;
    }
    
    /**
     * RabbitMQ Queue
     */
    @Bean
    @ConditionalOnProperty(name = "queue.type", havingValue = "rabbitmq")
    public Queue rabbitQueue() {
        String name = System.getenv("QUEUE_NAME");
        if (name == null || name.isEmpty()) {
            name = queueName;
        }
        return new Queue(name, queueDurable);
    }
    
    /**
     * RabbitMQ Template
     * ConnectionFactory가 null이 아닐 때만 생성
     */
    @Bean
    @ConditionalOnProperty(name = "queue.type", havingValue = "rabbitmq")
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            return null;
        }
        return new RabbitTemplate(connectionFactory);
    }
}

