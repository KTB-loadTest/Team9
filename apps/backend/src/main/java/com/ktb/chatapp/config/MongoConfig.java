package com.ktb.chatapp.config;

import com.mongodb.connection.ConnectionPoolSettings;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoPoolCustomizer() {
        return builder -> builder.applyToConnectionPoolSettings((ConnectionPoolSettings.Builder pool) -> {
            pool
                    .minSize(40)
                    .maxSize(100)
                    .maxWaitTime(1000, TimeUnit.MILLISECONDS)
                    .maxConnectionIdleTime(60, TimeUnit.SECONDS)
                    .maxConnectionLifeTime(0, TimeUnit.MILLISECONDS);
        });
    }
}
