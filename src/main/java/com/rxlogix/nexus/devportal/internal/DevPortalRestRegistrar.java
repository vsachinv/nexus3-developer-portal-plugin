package com.rxlogix.nexus.devportal.internal;

import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers the Developer Portal JAX-RS resources directly with Nexus's RESTEasy
 * deployment, bypassing ComponentContainerImpl's bean discovery.
 *
 * Why: Nexus 3.94 runs two Spring contexts. ComponentContainerImpl lives in a child
 * context and discovers resources via getBeansOfType(Component.class), which does NOT
 * traverse to the parent context where our @AutoConfiguration beans live. Our resources
 * are therefore invisible to it.
 *
 * How: Spring propagates the child context's ContextRefreshedEvent to parent-context
 * listeners. When that event arrives we resolve the ResteasyDeployment bean, then poll
 * until its Registry is live (getRegistry() is non-null only after
 * ResteasyDeployment.start(), which ComponentContainerImpl.init() invokes when Jetty
 * initializes the servlet) and add our resources as singletons. RESTEasy reads the
 * JAX-RS annotations off the instances; no Spring discovery is involved.
 *
 * Failure mode is deliberately soft: if registration never succeeds, the portal
 * endpoints return 404 and a warning is logged — Nexus itself is never affected.
 */
public class DevPortalRestRegistrar implements ApplicationListener<ContextRefreshedEvent> {

    private static final Logger log = LoggerFactory.getLogger(DevPortalRestRegistrar.class);

    private static final long POLL_INTERVAL_SECONDS = 2;
    private static final long GIVE_UP_AFTER_SECONDS = 300;

    private final List<Object> resources;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean registered = new AtomicBoolean();

    public DevPortalRestRegistrar(List<Object> resources) {
        this.resources = List.copyOf(resources);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        ResteasyDeployment deployment;
        try {
            deployment = context.getBean(ResteasyDeployment.class);
        } catch (Exception e) {
            // Not the context that hosts RESTEasy (e.g. our own parent context refresh)
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }
        log.info("Dev Portal: found ResteasyDeployment in context '{}', waiting for registry",
                context.getId());
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "devportal-rest-registrar");
            t.setDaemon(true);
            return t;
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GIVE_UP_AFTER_SECONDS);
        executor.scheduleWithFixedDelay(() -> {
            try {
                if (tryRegister(deployment)) {
                    executor.shutdown();
                }
                else if (System.nanoTime() > deadline) {
                    log.warn("Dev Portal: RESTEasy registry did not become available within {}s; "
                            + "portal endpoints will not be served", GIVE_UP_AFTER_SECONDS);
                    executor.shutdown();
                }
            } catch (Exception e) {
                log.warn("Dev Portal: unexpected error during REST registration", e);
                executor.shutdown();
            }
        }, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private boolean tryRegister(ResteasyDeployment deployment) {
        Registry registry = deployment.getRegistry();
        if (registry == null) {
            return false;
        }
        if (!registered.compareAndSet(false, true)) {
            return true;
        }
        for (Object resource : resources) {
            registry.addSingletonResource(resource);
            log.info("Dev Portal: registered REST resource {}", resource.getClass().getName());
        }
        log.info("Dev Portal: all {} REST resources registered with RESTEasy", resources.size());
        return true;
    }
}
