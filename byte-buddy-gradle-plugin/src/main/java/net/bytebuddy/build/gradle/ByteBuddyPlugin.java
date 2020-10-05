/*
 * Copyright 2014 - 2020 Rafael Winterhalter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.bytebuddy.build.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;

import java.lang.reflect.Method;

/**
 * A plugin for applying Byte Buddy transformations to all standard Java source sets.
 */
public class ByteBuddyPlugin implements Plugin<Project> {

    /**
     * If set to {@code true}, the Byte Buddy plugin will be configured as if running on a legacy version of Gradle.
     */
    public static final String LEGACY = "net.bytebuddy.build.gradle.legacy";

    /**
     * The dispatcher to use.
     */
    private static final Dispatcher<?, ?> DISPATCHER;

    /*
     * Resolves the dispatcher for the current Gradle API version.
     */
    static {
        Dispatcher<?, ?> dispatcher;
        if (Boolean.getBoolean(LEGACY)) {
            dispatcher = Dispatcher.ForLegacyGradle.INSTANCE;
        } else {
            try {
                Class.forName("org.gradle.work.InputChanges"); // Make sure Gradle 6 is available.
                dispatcher = new Dispatcher.ForApi6CapableGradle(SourceDirectorySet.class.getMethod("getDestinationDirectory"),
                        AbstractCompile.class.getMethod("setDestinationDir", Class.forName("org.gradle.api.provider.Provider")));
            } catch (Exception ignored) {
                dispatcher = Dispatcher.ForLegacyGradle.INSTANCE;
            }
        }
        DISPATCHER = dispatcher;
    }

    /**
     * {@inheritDoc}
     */
    public void apply(Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);
        Object convention = project.getConvention().getPlugins().get("java");
        if (convention instanceof JavaPluginConvention) {
            for (SourceSet sourceSet : ((JavaPluginConvention) convention).getSourceSets()) {
                String name = sourceSet.getName().equals("main") ? "byteBuddy" : (sourceSet.getName() + "ByteBuddy");
                project.getExtensions().add(name, DISPATCHER.toExtension());
                project.afterEvaluate(DISPATCHER.toAction(name, sourceSet));
            }
        } else {
            project.getLogger().warn("Skipping implicit Byte Buddy task configuration since Java plugin did not register Java plugin convention");
        }
    }

    /**
     * A dispatcher for creating Gradle integrations depending on the available API.
     *
     * @param <T> The Byte Buddy task type.
     * @param <S> The Byte Buddy extension type.
     */
    protected interface Dispatcher<T extends AbstractByteBuddyTask, S extends AbstractByteBuddyTaskExtension<T>> {

        /**
         * Creates a Byte Buddy extension instance.
         *
         * @return An appropriate Byte Buddy extension instance.
         */
        S toExtension();

        /**
         * Creates a Byte Buddy task configuration.
         *
         * @param name      The name of the task.
         * @param sourceSet The source set being configured.
         * @return An appropriate Byte Buddy task configuration.
         */
        AbstractByteBuddyTaskConfiguration<T, S> toAction(String name, SourceSet sourceSet);

        /**
         * A dispatcher for a legacy version of Gradle.
         */
        enum ForLegacyGradle implements Dispatcher<ByteBuddySimpleTask, ByteBuddySimpleTaskExtension> {

            /**
             * The singleton instance.
             */
            INSTANCE;

            /**
             * {@inheritDoc}
             */
            public ByteBuddySimpleTaskExtension toExtension() {
                return new ByteBuddySimpleTaskExtension();
            }

            /**
             * {@inheritDoc}
             */
            public ByteBuddySimpleTaskConfiguration toAction(String name, SourceSet sourceSet) {
                return new ByteBuddySimpleTaskConfiguration(name, sourceSet);
            }
        }

        /**
         * A dispatcher for a Gradle version of at least 6.
         */
        class ForApi6CapableGradle implements Dispatcher<ByteBuddyTask, ByteBuddyTaskExtension> {

            /**
             * The {@code org.gradle.api.file.SourceSetDirectory#getDestinationDirectory} method.
             */
            private final Method getDestinationDirectory;

            /**
             * The {@code org.gradle.api.tasks.compile.AbstractCompile#setDestinationDir} method.
             */
            private final Method setDestinationDir;

            /**
             * Creates a new dispatcher for a Gradle version of at least 6.
             *
             * @param getDestinationDirectory The {@code org.gradle.api.file.SourceSetDirectory#getDestinationDirectory} method.
             * @param setDestinationDir       The {@code org.gradle.api.tasks.compile.AbstractCompile#setDestinationDir} method.
             */
            protected ForApi6CapableGradle(Method getDestinationDirectory, Method setDestinationDir) {
                this.getDestinationDirectory = getDestinationDirectory;
                this.setDestinationDir = setDestinationDir;
            }

            /**
             * {@inheritDoc}
             */
            public ByteBuddyTaskExtension toExtension() {
                return new ByteBuddyTaskExtension();
            }

            /**
             * {@inheritDoc}
             */
            public ByteBuddyTaskConfiguration toAction(String name, SourceSet sourceSet) {
                return new ByteBuddyTaskConfiguration(name, sourceSet, getDestinationDirectory, setDestinationDir);
            }
        }
    }
}
