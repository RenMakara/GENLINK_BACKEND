package co.makara.istad.account.query.dataaccess.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Configuration
public class R2dbcConverterConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(
                PostgresDialect.INSTANCE,
                List.of(
                        new LocalDateTimeToZonedDateTimeConverter(),
                        new ZonedDateTimeToLocalDateTimeConverter()
                )
        );
    }

    @ReadingConverter
    static class LocalDateTimeToZonedDateTimeConverter implements Converter<LocalDateTime, ZonedDateTime> {
        @Override
        public ZonedDateTime convert(LocalDateTime source) {
            return source.atZone(ZoneId.systemDefault());
        }
    }

    @WritingConverter
    static class ZonedDateTimeToLocalDateTimeConverter implements Converter<ZonedDateTime, LocalDateTime> {
        @Override
        public LocalDateTime convert(ZonedDateTime source) {
            return source.toLocalDateTime();
        }
    }
}

