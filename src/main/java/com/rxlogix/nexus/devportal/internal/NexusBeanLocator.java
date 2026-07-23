package com.rxlogix.nexus.devportal.internal;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Locates Nexus-managed beans across Spring contexts.
 *
 * Nexus 3.94 registers its components (RepositoryManager, SecurityHelper, ...) in a
 * child ApplicationContext ('nexus-spring-component-scan') that is created after our
 * auto-configuration runs. getBean() on our own (parent) context cannot see them.
 *
 * Spring propagates every context's ContextRefreshedEvent to parent-context listeners,
 * so this listener collects each refreshed context and lookups try them newest-first.
 * Child-context lookups also traverse to the parent, so a single child hit covers both.
 */
public class NexusBeanLocator implements ApplicationListener<ContextRefreshedEvent> {

    private final List<ApplicationContext> contexts = new CopyOnWriteArrayList<>();

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        contexts.add(0, event.getApplicationContext());
    }

    public <T> Optional<T> lookup(Class<T> type) {
        for (ApplicationContext context : contexts) {
            try {
                return Optional.of(context.getBean(type));
            } catch (Exception e) {
                // not in this context; try the next
            }
        }
        return Optional.empty();
    }
}
