package org.grails.maven.plugin.tools;

import org.apache.maven.plugin.MojoExecutionException;
import org.grails.launcher.GrailsLauncher;
import org.grails.launcher.RootLoader;

import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Andrew Potter (ddcapotter)
 */
public abstract class AbstractGrailsRuntime {

    public AbstractGrailsRuntime(ExecutionContext context) {
        this.executionContext = context;
    }

    public abstract void run() throws MojoExecutionException;

    protected ExecutionContext executionContext;

    public static GrailsLauncher createGrailsLauncher(ExecutionContext ec) {
        URL[] urls = generateBuildPath(ec.getBuildDependencies());
        final RootLoader rootLoader = new RootLoader(urls, ClassLoader.getSystemClassLoader());
        System.setProperty("grails.console.enable.terminal", "false");
        System.setProperty("grails.console.enable.interactive", "false");

        List<File> compileDependencies = ec.getCompileDependencies();

        try {
            addLoggingJarsToRootLoader(rootLoader, compileDependencies);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        final GrailsLauncher launcher = new GrailsLauncher(rootLoader, null, ec.getBaseDir().getAbsolutePath());
        launcher.setPlainOutput(true);
        launcher.setDependenciesExternallyConfigured(true);
        launcher.setProvidedDependencies(ec.getProvidedDependencies());
        launcher.setCompileDependencies(compileDependencies);
        launcher.setTestDependencies(ec.getTestDependencies());
        launcher.setRuntimeDependencies(ec.getRuntimeDependencies());
        launcher.setGrailsWorkDir(ec.getGrailsWorkDir());
        launcher.setProjectWorkDir(ec.getProjectWorkDir());
        launcher.setClassesDir(ec.getClassesDir());
        launcher.setTestClassesDir(ec.getTestClassesDir());
        launcher.setResourcesDir(ec.getResourcesDir());
        launcher.setProjectPluginsDir(ec.getProjectPluginsDir());
        launcher.setBuildDependencies(ec.getBuildDependencies());

        return launcher;
    }

    public static void addLoggingJarsToRootLoader(RootLoader rootLoader, List<File> compileDependencies) throws MalformedURLException, ClassNotFoundException {
        List<File> loggingBootstrapJars = new ArrayList<File>();
        for (File file : compileDependencies) {
            String name = file.getName();
            if(name.contains("slf4j") || name.contains("log4j") || name.contains("spring-core") || name.contains("logback")) {
                loggingBootstrapJars.add(file);
            }
        }
        if(!loggingBootstrapJars.isEmpty()) {
            for (File loggingBootstrapJar : loggingBootstrapJars) {
                rootLoader.addURL(loggingBootstrapJar.toURI().toURL());
            }
            Class cls = rootLoader.loadClass("org.springframework.util.Log4jConfigurer");
            try {
                invokeStaticMethod(cls, "initLogging", new Object[]{"classpath:grails-maven/log4j.properties"});
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    /**
     * Invokes the named method on a target object using reflection.
     * The method signature is determined by the classes of each argument.
     * @param target The object to call the method on.
     * @param name The name of the method to call.
     * @param args The arguments to pass to the method (may be an empty array).
     * @return The value returned by the method.
     */
    private static Object invokeStaticMethod(Class target, String name, Object[] args) {
        Class<?>[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }

        try {
            return target.getMethod(name, argTypes).invoke(target, args);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    protected static void fatalError(Throwable e) {
        System.out.println();
        System.out.println("Fatal error forking Grails JVM: " + e.getMessage());
        e.printStackTrace(System.out);
        System.out.flush();
        System.exit(1);
    }

    protected static URL[] generateBuildPath(List<File> systemDependencies) {
        List<URL> urls = new ArrayList<URL>();
        for (File systemDependency : systemDependencies) {
            try {
                urls.add(systemDependency.toURI().toURL());
            } catch (MalformedURLException e) {
                // ignore
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    public static class ExecutionContext implements Serializable {
        private List<File> compileDependencies;
        private List<File> runtimeDependencies;
        private List<File> buildDependencies;
        private List<File> providedDependencies;
        private List<File> testDependencies;
        private List forkedVmArgs;

        private File grailsWorkDir;
        private File projectWorkDir;
        private File classesDir;
        private File testClassesDir;
        private File resourcesDir;
        private File projectPluginsDir;
        private File baseDir;
        private File dependencyFileLocation;

        private String scriptName;
        private String env;
        private String args;
        private String grailsBuildListener;


        public String getScriptName() {
            return scriptName;
        }

        public void setScriptName(String scriptName) {
            this.scriptName = scriptName;
        }

        public String getEnv() {
            return env;
        }

        public void setEnv(String env) {
            this.env = env;
        }

        public String getArgs() {
            return args;
        }

        public void setArgs(String args) {
            this.args = args;
        }

        public List<String> getForkedVmArgs() {
            return forkedVmArgs;
        }

        public void setForkedVmArgs(List<String> forkedVmArgs) {
            this.forkedVmArgs = forkedVmArgs;
        }

        public File getBaseDir() {
            return baseDir;
        }

        public void setBaseDir(File baseDir) {
            this.baseDir = baseDir;
        }

        public List<File> getCompileDependencies() {
            return compileDependencies;
        }

        public void setCompileDependencies(List<File> compileDependencies) {
            this.compileDependencies = compileDependencies;
        }

        public List<File> getRuntimeDependencies() {
            return runtimeDependencies;
        }

        public void setRuntimeDependencies(List<File> runtimeDependencies) {
            this.runtimeDependencies = runtimeDependencies;
        }

        public List<File> getBuildDependencies() {
            return buildDependencies;
        }

        public void setBuildDependencies(List<File> buildDependencies) {
            this.buildDependencies = buildDependencies;
        }

        public List<File> getProvidedDependencies() {
            return providedDependencies;
        }

        public void setProvidedDependencies(List<File> providedDependencies) {
            this.providedDependencies = providedDependencies;
        }

        public List<File> getTestDependencies() {
            return testDependencies;
        }

        public void setTestDependencies(List<File> testDependencies) {
            this.testDependencies = testDependencies;
        }

        public File getGrailsWorkDir() {
            return grailsWorkDir;
        }

        public void setGrailsWorkDir(File grailsWorkDir) {
            this.grailsWorkDir = grailsWorkDir;
        }

        public File getProjectWorkDir() {
            return projectWorkDir;
        }

        public void setProjectWorkDir(File projectWorkDir) {
            this.projectWorkDir = projectWorkDir;
        }

        public File getClassesDir() {
            return classesDir;
        }

        public void setClassesDir(File classesDir) {
            this.classesDir = classesDir;
        }

        public File getTestClassesDir() {
            return testClassesDir;
        }

        public void setTestClassesDir(File testClassesDir) {
            this.testClassesDir = testClassesDir;
        }

        public File getResourcesDir() {
            return resourcesDir;
        }

        public void setResourcesDir(File resourcesDir) {
            this.resourcesDir = resourcesDir;
        }

        public File getProjectPluginsDir() {
            return projectPluginsDir;
        }

        public void setProjectPluginsDir(File projectPluginsDir) {
            this.projectPluginsDir = projectPluginsDir;
        }

        public File getDependencyFileLocation() {
            return dependencyFileLocation;
        }

        public void setDependencyFileLocation(File dependencyFile) {
            this.dependencyFileLocation = dependencyFile;
        }

        public String getGrailsBuildListener() {
            return grailsBuildListener;
        }

        public void setGrailsBuildListener(String buildListener) {
            this.grailsBuildListener = buildListener;
        }
    }
}
