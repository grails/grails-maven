package org.grails.maven.plugin.tools;

import groovy.lang.GroovyRuntimeException;
import org.grails.launcher.GrailsLauncher;
import org.grails.launcher.RootLoader;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 
 * Allows for Grails commands to be executed in a Forked VM
 * 
 * @author Graeme Rocher
 * @since 2.1
 */
public class ForkedGrailsRuntime {
    
    
    private ExecutionContext executionContext;
    private int maxMemory = 1024;
    private int minMemory = 512;
    private int maxPerm = 256;
    private boolean debug;

    public ForkedGrailsRuntime(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public void setMaxMemory(int maxMemory) {
        this.maxMemory = maxMemory;
    }

    public void setMinMemory(int minMemory) {
        this.minMemory = minMemory;
    }

    public void setMaxPerm(int maxPerm) {
        this.maxPerm = maxPerm;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void fork() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        StringBuilder cp = new StringBuilder();
        for (File file : executionContext.getBuildDependencies()) {
            cp.append(file).append(File.pathSeparator);
        }


        FileOutputStream fos = null;
        File tempFile = null;
        try {
            String baseName = executionContext.getBaseDir().getCanonicalFile().getName();
            tempFile = File.createTempFile(baseName, "grails-execution-context");
            tempFile.deleteOnExit();
            
            fos = new FileOutputStream(tempFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(executionContext);


            List<String> cmd = new ArrayList<String>(Arrays.asList("java", "-Xmx" + maxMemory + "M", "-Xms" + minMemory + "M", "-XX:MaxPermSize=" + maxPerm + "m", "-Dgrails.build.execution.context=" + tempFile.getCanonicalPath(), "-cp", cp.toString()));
            if(debug) {
                cmd.addAll(Arrays.asList("-Xdebug","-Xnoagent","-Dgrails.full.stacktrace=true", "-Djava.compiler=NONE", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"));
            }
            cmd.add(getClass().getName());
            processBuilder
                    .directory(executionContext.baseDir)
                    .redirectErrorStream(true)
                    .command(cmd);

            Process process = processBuilder.start();

            new Thread(new TextDumper(process.getInputStream(), System.out)).start();
            new Thread(new TextDumper(process.getErrorStream(), System.err)).start();

            int result = process.waitFor();
            if(result == 1) {

                throw new RuntimeException("Forked Grails VM exited with error");
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Fatal error forking Grails JVM: " + e.getMessage() , e);
        } catch (IOException e) {
            throw new RuntimeException("Fatal error forking Grails JVM: " + e.getMessage() , e);
        } catch (InterruptedException e) {
            throw new RuntimeException("Fatal error forking Grails JVM: " + e.getMessage() , e);
        } finally {
            if(fos  != null) try {
                fos.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    public static void main(String[] args) {
        String location = System.getProperty("grails.build.execution.context");
        if(location != null) {
            File f = new File(location);
            FileInputStream fis = null;

            try {
                fis = new FileInputStream(f);
                ObjectInputStream ois = new ObjectInputStream(fis);
                ExecutionContext ec = (ExecutionContext) ois.readObject();

                URL[] urls = generateBuildPath(ec.getBuildDependencies());
                final RootLoader rootLoader = new RootLoader(urls, ClassLoader.getSystemClassLoader());
                System.setProperty("grails.console.enable.terminal", "false");
                System.setProperty("grails.console.enable.interactive", "false");

                List<File> loggingBootstrapJars = new ArrayList<File>();
                for (File file : ec.getCompileDependencies()) {
                    String name = file.getName();
                    if(name.contains("slf4j") || name.contains("log4j") || name.contains("spring-core")) {
                        loggingBootstrapJars.add(file);
                    }
                }
                if(!loggingBootstrapJars.isEmpty()) {
                    for (File loggingBootstrapJar : loggingBootstrapJars) {
                        rootLoader.addURL(loggingBootstrapJar.toURI().toURL());
                    }
                    Class cls = rootLoader.loadClass("org.springframework.util.Log4jConfigurer");
                    invokeStaticMethod(cls, "initLogging", new Object[]{"classpath:grails-maven/log4j.properties"});
                }


                final GrailsLauncher launcher = new GrailsLauncher(rootLoader, null, ec.getBaseDir().getAbsolutePath());
                launcher.setPlainOutput(true);
                launcher.setDependenciesExternallyConfigured(true);
                launcher.setProvidedDependencies(ec.getProvidedDependencies());
                launcher.setCompileDependencies(ec.getCompileDependencies());
                launcher.setTestDependencies(ec.getTestDependencies());
                launcher.setRuntimeDependencies(ec.getRuntimeDependencies());
                launcher.setGrailsWorkDir(ec.getGrailsWorkDir());
                launcher.setProjectWorkDir(ec.getProjectWorkDir());
                launcher.setClassesDir(ec.getClassesDir());
                launcher.setTestClassesDir(ec.getTestClassesDir());
                launcher.setResourcesDir(ec.getResourcesDir());
                launcher.setProjectPluginsDir(ec.getProjectPluginsDir());
                launcher.setBuildDependencies(ec.getBuildDependencies());
                System.exit( launcher.launch(ec.getScriptName(), ec.getArgs(), ec.getEnv()) );

            } catch (FileNotFoundException e) {
                fatalError(e);
            } catch (ClassNotFoundException e) {
                fatalError(e);
            } catch (IOException e) {
                fatalError(e);
            } catch( Throwable e) {
                fatalError(e);
            }
            finally {
                if(fis != null)  {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
        else {
            System.exit(1);
        }
    }

    protected static void fatalError(Throwable e) {
        System.err.println("Fatal error forking Grails JVM: " + e.getMessage());
        e.printStackTrace(System.err);
        System.exit(1);
    }

    private static URL[] generateBuildPath(List<File> systemDependencies) {
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

    public static class ExecutionContext implements Serializable {
        private List<File> compileDependencies;
        private List<File> runtimeDependencies;
        private List<File> buildDependencies;
        private List<File> providedDependencies;
        private List<File> testDependencies;
        
        private File grailsWorkDir;
        private File projectWorkDir;
        private File classesDir;
        private File testClassesDir;
        private File resourcesDir;
        private File projectPluginsDir;
        private File baseDir;
        
        private String scriptName;
        private String env;
        private String args;

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
    }

    private static class TextDumper implements Runnable {
        InputStream in;
        Appendable app;

        public TextDumper(InputStream in, Appendable app) {
            this.in = in;
            this.app = app;
        }

        public void run() {
            InputStreamReader isr = new InputStreamReader(in);
            BufferedReader br = new BufferedReader(isr);
            String next;
            try {
                while ((next = br.readLine()) != null) {
                    if (app != null) {
                        app.append(next);
                        app.append("\n");
                    }
                }
            } catch (IOException e) {
                throw new GroovyRuntimeException("exception while reading process stream", e);
            }
        }
    }    
}
