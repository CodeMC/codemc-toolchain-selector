package io.codemc.maven.plugins.toolchain;

import com.google.common.collect.Maps;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginContainer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.MisconfiguredToolchainException;
import org.apache.maven.toolchain.ToolchainManagerPrivate;
import org.apache.maven.toolchain.ToolchainPrivate;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.*;

@Mojo(name = "select", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class ToolchainSelectorMojo extends AbstractMojo {
    private static final Object LOCK = new Object();

    @Component
    private ToolchainManagerPrivate toolchainManagerPrivate;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter(property = "toolchainType")
    String toolchainDefinition;

    @Parameter( property = "maven.compiler.target")
    protected String target;

    @Override
    public void execute() throws MojoExecutionException {

        String type;
        String version;
        if (toolchainDefinition == null) {
            type = "jdk";

            Plugin compilerPlugin = Optional.ofNullable(project.getPlugin("org.apache.maven.plugins:maven-compiler-plugin"))
                    .orElseGet(() -> Optional.ofNullable(project.getPluginManagement())
                            .map(PluginContainer::getPluginsAsMap)
                            .map(pluginMap -> pluginMap.get("org.apache.maven.plugins:maven-compiler-plugin"))
                            .orElse(null));

            version = Optional.ofNullable(compilerPlugin)
                    .map(plugin -> (Xpp3Dom) plugin.getConfiguration())
                    .map(config -> Optional.ofNullable(config.getChild("target"))
                            .orElseGet(() -> config.getChild("release")))
                    .map(Xpp3Dom::getValue)
                    .orElse(target);

            if (version == null) {
                getLog().warn("No specific JDK version requested in the pom file! Falling back to the current maven toolchain.");
                return;
            }

            if (version.equals("8")) {
                version = "1.8";
            }
        } else {
            String[] splitted = toolchainDefinition.split(":");
            if (splitted.length != 2) {
                throw new MojoExecutionException("Invalid toolchain definition string!");
            }
            type = splitted[0];
            version = splitted[1];
        }

        getLog().info("Looking for toolchain " + type + ":" + version);

        Map<String, String> requirements = new HashMap<>();
        requirements.put("version", version);
        try {
            ToolchainPrivate[] toolchains = toolchainManagerPrivate.getToolchainsForType(type, session);
            ToolchainPrivate selected = Arrays.stream(toolchains)
                    .filter(toolchain -> toolchain.matchesRequirements(requirements))
                    .findFirst().orElse(null);
            if (selected == null) {
                getLog().info("No toolchain for " + type + ":" + version + ", continuing with the default toolchain...");
                return;
            }
            getLog().info("Found toolchain for " + type + ":" + version);
            synchronized (LOCK) {
                toolchainManagerPrivate.storeToolchainToBuildContext(selected, session);
            }
        } catch (MisconfiguredToolchainException e) {
            throw new MojoExecutionException("An error occurred", e);
        }
    }
}
