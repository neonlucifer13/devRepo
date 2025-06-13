package com.devrepo.core.service.impl;

import com.devrepo.core.configurations.AesConfig;
import com.devrepo.core.service.AesConfigService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = AesConfigService.class, immediate = true)
@Designate(ocd = AesConfig.class)
public class AesConfigServiceImpl implements AesConfigService {

    private static final Logger log = LoggerFactory.getLogger(AesConfigServiceImpl.class);
    private String secretKey;
    private AesConfig aesConfig;

    @Activate
    @Modified
    private void activate(AesConfig aesConfig) {
        this.aesConfig = aesConfig;
    }

    @Override
    public String getSecretKey() {
        log.info(aesConfig.secretKey());
        return aesConfig.secretKey();
    }
}


