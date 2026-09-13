package cn.hospital.eph.common.config;

import cn.hospital.eph.common.mq.OutboxEventMapper;
import cn.hospital.eph.common.mq.OutboxRelay;
import cn.hospital.eph.common.mq.OutboxService;
import cn.hospital.eph.common.security.JwtAuthFilter;
import cn.hospital.eph.common.security.JwtService;
import cn.hospital.eph.common.security.RequireRoleAspect;
import cn.hospital.eph.common.web.GlobalExceptionHandler;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

/** eph-common 统一自动装配（JWT/Web/MyBatis-Plus/AMQP/Outbox）。gateway 为 WebFlux 不引本模块。 */
@Configuration
@EnableScheduling
@MapperScan("cn.hospital.eph.common.mq")
public class EphCommonAutoConfiguration {

    @Bean
    public JwtService jwtService() {
        return new JwtService();
    }

    @Bean
    public RequireRoleAspect requireRoleAspect() {
        return new RequireRoleAspect();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtService jwtService) {
        FilterRegistrationBean<JwtAuthFilter> bean =
                new FilterRegistrationBean<>(new JwtAuthFilter(jwtService));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public OutboxService outboxService(OutboxEventMapper mapper) {
        return new OutboxService(mapper);
    }

    @Bean
    public OutboxRelay outboxRelay(RabbitTemplate rabbitTemplate, OutboxEventMapper mapper) {
        return new OutboxRelay(rabbitTemplate, mapper);
    }
}
