package com.ktb.chatapp.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ReadFrom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.RedisStaticMasterReplicaConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.master.host}")
    private String masterHost;

    @Value("${spring.data.redis.master.port}")
    private Integer masterPort;

    @Value("${spring.data.redis.replica.host:}")
    private String replicaHost;

    @Value("${spring.data.redis.replica.port:0}")
    private Integer replicaPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        boolean hasReplica = replicaHost != null && !replicaHost.isBlank()
                && replicaPort != null && replicaPort > 0;

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .readFrom(hasReplica ? ReadFrom.MASTER_PREFERRED : ReadFrom.MASTER)
                .build();

        if (hasReplica) {
            RedisStaticMasterReplicaConfiguration redisConfig =
                    new RedisStaticMasterReplicaConfiguration(masterHost, masterPort);
            redisConfig.addNode(replicaHost, replicaPort);
            return new LettuceConnectionFactory(redisConfig, clientConfig);
        }

        RedisStandaloneConfiguration standaloneConfig =
                new RedisStandaloneConfiguration(masterHost, masterPort);
        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory());

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer(customObjectMapper()));
        return redisTemplate;
    }

    private ObjectMapper customObjectMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 타입 정보를 포함해 세션 등 도메인 객체를 올바르게 역직렬화
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

}
