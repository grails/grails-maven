package org.grails.maven.plugin.tools;

import groovy.lang.GroovyRuntimeException;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.groovy.grails.io.support.GrailsIOUtils;
import org.grails.launcher.GrailsLauncher;
import org.grails.launcher.RootLoader;
import org.grails.maven.plugin.AbstractGrailsMojo;

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
public class ForkedGrailsRuntime extends AbstractGrailsRuntime {
    
    
    private int maxMemory = 1024;
    private int minMemory = 512;
    private int maxPerm = 256;
    private boolean debug;
    private File reloadingAgent;

    public ForkedGrailsRuntime(ExecutionContext executionContext) {
        super(executionContext);
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

    public void run() throws MojoExecutionException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        StringBuilder cp = new StringBuilder();
        cp.append(GrailsIOUtils.findJarFile(ForkedGrailsRuntime.class)).append(File.pathSeparatorChar);
        for (File file : executionContext.getBuildDependencies()) {
            cp.append(file).append(File.pathSeparatorChar);
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
            if(reloadingAgent != null) {
                cmd.addAll(Arrays.asList("-javaagent:" + reloadingAgent.getCanonicalPath(), "-noverify", "-Dspringloaded=profile=grails"));
            }

            if(null != executionContext.getForkedVmArgs()
            && executionContext.getForkedVmArgs().size() > 0) {
                cmd.addAll(executionContext.getForkedVmArgs());
            }

            // For use inside of IDEs
            if (executionContext.getGrailsBuildListener() != null) {
                cmd.add("-D" + AbstractGrailsMojo.GRAILS_BUILD_LISTENERS + "=" + executionContext.getGrailsBuildListener());
            }
            // For use inside of IDEs
            if (executionContext.getDependencyFileLocation() != null) {
                cmd.add("-D" + AbstractGrailsMojo.DEPENDENCY_FILE_LOC + "=" + executionContext.getDependencyFileLocation());
            }
            
            cmd.add(getClass().getName());
            processBuilder
                    .directory(executionContext.getBaseDir())
                    .redirectErrorStream(false)
                    .command(cmd);

            Process process = processBuilder.start();

            InputStream is = process.getInputStream();
            InputStream es = process.getErrorStream();
            Thread t1 = new Thread(new TextDumper(is, System.out));
            Thread t2 = new Thread(new TextDumper(es, System.err));
            t1.start();
            t2.start();

            int result = process.waitFor();
            if(result == 1) {
                try { t1.join(); } catch (InterruptedException ignore) {}
                try { t1.join(); } catch (InterruptedException ignore) {}
                try { es.close(); } catch (IOException ignore) {}
                try { is.close(); } catch (IOException ignore) {}

                System.out.flush();
                System.err.flush();

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

                GrailsLauncher launcher = createGrailsLauncher(ec);
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

    public void setReloadingAgent(File file) {
        this.reloadingAgent = file;
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
