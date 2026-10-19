package com.nigson.integration.provider;
import java.util.UUID;
@FunctionalInterface public interface CareerSheetContextPort { CareerSheetContextClient.Context read(UUID applicationId,UUID correlationId); }
