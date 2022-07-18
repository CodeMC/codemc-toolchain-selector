package io.codemc.maven.plugins.toolchain;

import com.google.common.collect.Maps;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Mojo(name = "select", defaultPhase = LifecyclePhase.VALIDATE)
public class ToolchainSelectorMojo extends AbstractMojo {

    @Component
    private ToolchainManagerPrivate toolchainManagerPrivate;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    MavenProject project;

    @Parameter(property = "toolchainType", required = false)
    String toolchainDefinition;

    @Parameter( property = "maven.compiler.target")
    protected String target;

    @Override
    public void execute() throws MojoExecutionException {
        Plugin compilerPlugin = project.getPlugin("org.apache.maven.plugins:maven-compiler-plugin");

        String type;
        String version = null;
        if (toolchainDefinition == null) {
            type = "jdk";
            if (compilerPlugin != null) {
                Xpp3Dom config = (Xpp3Dom) compilerPlugin.getConfiguration();
                if (config != null) {
                    Xpp3Dom target = config.getChild("target");
                    if (target != null) {
                        version = target.getValue();
                    } else {
                        Xpp3Dom release = config.getChild("release");
                        if (release != null) {
                            version = release.getValue();
                        }
                    }
                }
            }
            if (version == null) {
                version = target;
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
            toolchainManagerPrivate.storeToolchainToBuildContext(selected, session);
        } catch (MisconfiguredToolchainException e) {
            throw new MojoExecutionException("An error occurred", e);
        }
    }
}
