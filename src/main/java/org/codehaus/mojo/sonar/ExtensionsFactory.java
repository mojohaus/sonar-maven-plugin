package org.codehaus.mojo.sonar;

import java.util.LinkedList;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProjectBuilder;

public class ExtensionsFactory
{
    private final MavenSession session;

    private final LifecycleExecutor lifecycleExecutor;

    private final Log log;

    private final MavenProjectBuilder projectBuilder;

    public ExtensionsFactory( Log log, MavenSession session, LifecycleExecutor lifecycleExecutor,
                              MavenProjectBuilder projectBuilder )
    {
        this.log = log;
        this.session = session;
        this.lifecycleExecutor = lifecycleExecutor;
        this.projectBuilder = projectBuilder;
    }

    public List<Object> createExtensionsWithDependencyProperty()
    {
        List<Object> extensions = new LinkedList<>();

        extensions.add( log );
        extensions.add( session );
        extensions.add( lifecycleExecutor );
        extensions.add( projectBuilder );

        return extensions;
    }

}
