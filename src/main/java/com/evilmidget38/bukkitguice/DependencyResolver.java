package com.evilmidget38.bukkitguice;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

public class DependencyResolver {
    private static Logger logger = LoggerFactory.getLogger(DependencyResolver.class);

    private Collection<AfterContextInitialize> resolvedBeans = Lists.newArrayList();

    private Map<Class<? extends AfterContextInitialize>, AfterContextInitialize> notResolvedBeans;

    DependencyResolver(Collection<? extends AfterContextInitialize> beans) {
        this.notResolvedBeans = Maps.newHashMap();

        for (AfterContextInitialize bean : beans) {
            this.notResolvedBeans.put(bean.getClass(), bean);
        }
    }

    public void resolve() {
        try {
            for (AfterContextInitialize bean : this.notResolvedBeans.values()) {
                this.resolve(bean);
            }
        } catch (Exception e) {
            logger.error("Resolving dependencies failed", e);
            throw new RuntimeException(e);
        }
    }

    private void resolve(AfterContextInitialize bean) throws Exception {
        if (this.resolvedBeans.contains(bean)) {
            return;
        }

        Method method = bean.getClass().getDeclaredMethod("contextInitialized");

        DependsOn annotation = method.getAnnotation(DependsOn.class);

        if (annotation != null) {
            for (Class<? extends AfterContextInitialize> clasz : annotation.value()) {
                AfterContextInitialize otherBean = this.notResolvedBeans.get(clasz);

                if (otherBean == null) {
                    throw new RuntimeException("Class not found");
                }

                this.resolve(otherBean);
            }
        }

        logger.info("Resolving bean " + bean.getClass().getName());
        bean.contextInitialized();
        this.resolvedBeans.add(bean);
    }
}
