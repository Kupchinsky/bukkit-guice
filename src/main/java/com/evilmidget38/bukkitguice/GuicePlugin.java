package com.evilmidget38.bukkitguice;

import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.spi.DefaultBindingScopingVisitor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * A utility class for easily adding BukkitGuice support.
 */
public class GuicePlugin extends JavaPlugin {
    @Override
    public final void onEnable() {
        BukkitGuice guice = new BukkitGuice(this);
        configure(guice);
        try {
            guice.start();
        } catch (InitializationFailedException e) {
            getLogger().log(Level.SEVERE, "Failed to start " + getDescription().getName(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        final List<AfterContextInitialize> initializeBeans = Lists.newArrayList();

        for (Map.Entry<Key<?>, Binding<?>> entry : guice.getInjector().getAllBindings().entrySet()) {
            if (AfterContextInitialize.class.isAssignableFrom(entry.getKey().getTypeLiteral().getRawType())) {
                final Binding<?> binding = entry.getValue();
                binding.acceptScopingVisitor(new DefaultBindingScopingVisitor<Void>() {
                    @Override
                    public Void visitEagerSingleton() {
                        AfterContextInitialize instance = (AfterContextInitialize) (binding.getProvider().get());
                        initializeBeans.add(instance);

                        return null;
                    }
                });
            }
        }

        DependencyResolver dependencyResolver = new DependencyResolver(initializeBeans);
        dependencyResolver.resolve();

        start();
    }

    public void configure(BukkitGuice guice) {
    }

    public void start() {
    }
}
