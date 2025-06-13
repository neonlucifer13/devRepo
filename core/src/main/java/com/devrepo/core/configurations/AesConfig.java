package com.devrepo.core.configurations;

import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.service.metatype.annotations.AttributeDefinition;

@ObjectClassDefinition(
        name = "AES Configuration",
        description = "Provides AES encryption key"
)
public @interface AesConfig {

    @AttributeDefinition(
            name = "Secret Key",
            description = "AES Secret Key (16/24/32 chars)"
    )
    String secretKey() default "DefaultSecret123";
}



