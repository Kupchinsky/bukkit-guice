package com.evilmidget38.bukkitguice;

import com.evilmidget38.bukkitguice.command.CommandHandler;
import com.evilmidget38.bukkitguice.command.CommandInitializer;
import com.evilmidget38.bukkitguice.config.ConfigProcessor;
import com.evilmidget38.bukkitguice.injectors.TypeListenerBinding;
import com.evilmidget38.bukkitguice.listener.ListenerHandler;
import com.evilmidget38.bukkitguice.listener.ListenerInitializer;
import com.evilmidget38.bukkitguice.plugin.PluginProcessor;
import com.evilmidget38.bukkitguice.scanning.ClassHandler;
import com.evilmidget38.bukkitguice.services.DefaultServiceManager;
import com.evilmidget38.bukkitguice.services.PluginServiceHandler;
import com.evilmidget38.bukkitguice.services.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;

public class InternalModule extends AbstractModule {
    private final JavaPlugin plugin;

    public InternalModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(TypeLiterals.SET_CLASS).annotatedWith(Names.named("discovered")).toInstance(new HashSet<Class<?>>());
        bind(JavaPlugin.class).toInstance(plugin);
        bind(ServiceManager.class).to(DefaultServiceManager.class);
        Multibinder<Module> modules = Multibinder.newSetBinder(binder(), Module.class);
        Multibinder<TypeListenerBinding> typeListeners = Multibinder.newSetBinder(binder(), TypeListenerBinding.class);
        typeListeners.addBinding().to(ConfigProcessor.class);
        typeListeners.addBinding().to(PluginProcessor.class);
        Multibinder<ClassHandler> classHandlers = Multibinder.newSetBinder(binder(), ClassHandler.class);
        classHandlers.addBinding().to(ListenerHandler.class);
        classHandlers.addBinding().to(CommandHandler.class);
        classHandlers.addBinding().to(PluginServiceHandler.class);
        Multibinder<ObjectInitializer> initializers = Multibinder.newSetBinder(binder(), ObjectInitializer.class);
        initializers.addBinding().to(CommandInitializer.class);
        initializers.addBinding().to(ListenerInitializer.class);

        bindListener(Matchers.any(), new LoggerTypeListener());
        bindListener(Matchers.any(), new AfterContextInitializeTypeListener());
    }

    public static class AfterContextInitializeTypeListener implements TypeListener {
        public <T> void hear(TypeLiteral<T> typeLiteral, TypeEncounter<T> typeEncounter) {
            Class<?> clazz = typeLiteral.getRawType();

            if (AfterContextInitialize.class.isAssignableFrom(clazz)) {
                typeEncounter.register(new AfterContextInitializeInjectionListener<>());
            }
        }
    }

    public static class AfterContextInitializeInjectionListener<T> implements InjectionListener<T> {
        private static Logger logger = LoggerFactory.getLogger(AfterContextInitializeInjectionListener.class);

        @Override
        public void afterInjection(T instance) {
            try {
                ((AfterContextInitialize) instance).contextInitialized();
            } catch (Exception e) {
                logger.error("Unable to initialize class {}", instance.getClass().getName(), e);
                throw new RuntimeException(e);
            }
        }
    }

    public static class LoggerTypeListener implements TypeListener {
        public <T> void hear(TypeLiteral<T> typeLiteral, TypeEncounter<T> typeEncounter) {
            Class<?> clazz = typeLiteral.getRawType();
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (field.getType() == Logger.class &&
                            Modifier.isStatic(field.getModifiers()) &&
                            Modifier.isPrivate(field.getModifiers())) {
                        typeEncounter.register(new LoggerMembersInjector<>(field));
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
    }

    public static class LoggerMembersInjector<T> implements MembersInjector<T> {
        private final Field field;
        private final Logger logger;

        LoggerMembersInjector(Field field) {
            this.field = field;
            this.logger = LoggerFactory.getLogger(field.getDeclaringClass().getSimpleName());
            field.setAccessible(true);
        }

        public void injectMembers(T t) {
            try {
                field.set(t, logger);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
