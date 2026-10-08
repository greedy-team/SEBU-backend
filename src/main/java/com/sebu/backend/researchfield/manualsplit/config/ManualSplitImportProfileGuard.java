package com.sebu.backend.researchfield.manualsplit.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile({
    "research-field-manual-split & crawler",
    "research-field-manual-split & promotion",
    "research-field-manual-split & research-field-extraction"
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManualSplitImportProfileGuard implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        throw new IllegalStateException("MANUAL_SPLIT_IMPORT_PROFILE_CONFLICT");
    }
}
