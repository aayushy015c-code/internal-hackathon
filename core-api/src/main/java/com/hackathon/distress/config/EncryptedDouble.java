package com.hackathon.distress.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

// Same as FieldEncryptor, for numbers (location, voice baseline).
@Component
@Converter
public class EncryptedDouble implements AttributeConverter<Double, String> {

    private final FieldEncryptor encryptor;

    public EncryptedDouble(FieldEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(Double value) {
        return value == null ? null : encryptor.convertToDatabaseColumn(value.toString());
    }

    @Override
    public Double convertToEntityAttribute(String stored) {
        return stored == null ? null : Double.valueOf(encryptor.convertToEntityAttribute(stored));
    }
}
