package com.evilmidget38.bukkitguice;

import com.evilmidget38.bukkitguice.services.ServiceManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BukkitGuice {
    private final JavaPlugin plugin;

    private final List<Module> internalModules = Lists.newArrayList();
    private static final Map<BukkitGuice, Injector> injectors = Maps.newConcurrentMap();

    public BukkitGuice(JavaPlugin plugin) {
        this.plugin = plugin;
        this.internalModules.add(new InternalModule(plugin, this));
    }

    public void injectMembers(Object instance) {
        this.getInjector().injectMembers(instance);
    }

    public List<Module> getInternalModules() {
        return internalModules;
    }

    public Injector getInjector() {
        return injectors.get(this);
    }

    @SuppressWarnings("unchecked")
    public void start() throws InitializationFailedException {
        try {
            Injector injector = Guice.createInjector(internalModules);
            injectors.put(this, injector);

            ServiceManager serviceManager = injector.getInstance(ServiceManager.class);
            serviceManager.validateServices(plugin.getLogger());
            Set<Module> childModules = injector.getInstance(Key.get(TypeLiterals.SET_MODULE));
            Injector childInjector = injector.createChildInjector(childModules);

            // @Inject @Named("discovered") Set<Class<?>>
            for (Class<?> clazz : injector.getInstance(Key.get(TypeLiterals.SET_CLASS, Names.named("discovered")))) {
                Object object = childInjector.getInstance(clazz);
                for (ObjectInitializer initializer : injector.getInstance(Key.get(TypeLiterals.SET_OBJECT_INITIALIZER))) {
                    if (initializer.getType().isAssignableFrom(clazz)) {
                        initializer.initialize(plugin, object);
                    }
                }
            }
        } catch (Exception e) {
            throw new InitializationFailedException(e);
        }
    }

}
