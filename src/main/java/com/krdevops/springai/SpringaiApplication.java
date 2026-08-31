package com.krdevops.springai;

import org.springframework.ai.vectorstore.redis.autoconfigure.RedisVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = RedisVectorStoreAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class SpringaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringaiApplication.class, args);
    }

}
