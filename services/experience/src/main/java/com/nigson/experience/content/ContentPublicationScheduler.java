package com.nigson.experience.content;

import org.springframework.scheduling.annotation.Scheduled;

/** Advances due approved content with owner-local locks and durable publication events. */
public final class ContentPublicationScheduler {
 private final CmsService cms;ContentPublicationScheduler(CmsService cms){this.cms=cms;}
 @Scheduled(fixedDelayString="${NIGSON_CONTENT_PUBLICATION_INTERVAL_MS:5000}")
 public void publishDue(){cms.publishScheduled(50);}
}
