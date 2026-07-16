package com.rpb.reservation.queuedisplay.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class CallScreenMediaUploadConfigurationTest {
    @Test
    void multipartLimitsAllowCallScreenVideoUploadsUpToBusinessLimit() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        Properties properties = yaml.getObject();

        assertThat(properties)
            .containsEntry("spring.servlet.multipart.max-file-size", "80MB")
            .containsEntry("spring.servlet.multipart.max-request-size", "90MB")
            .containsEntry("server.tomcat.max-swallow-size", "100MB");
    }
}
